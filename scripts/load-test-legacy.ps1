param(
  [string]$BaseUrl = "http://localhost:8080",
  [int]$RequestsPerScenario = 30,
  [string]$AdminUser = "admin",
  [string]$AdminPass = "tu-contrasena-segura"
)

$ErrorActionPreference = "Stop"

function Invoke-JsonPost($Url, $BodyObj, $Token = $null) {
  $Headers = @{ "Content-Type" = "application/json" }
  if ($Token) { $Headers["Authorization"] = "Bearer $Token" }
  $Json = $BodyObj | ConvertTo-Json -Depth 6
  return Invoke-RestMethod -Uri $Url -Method POST -Headers $Headers -Body $Json
}

function Invoke-JsonPatch($Url, $BodyObj, $Token) {
  $Headers = @{
    "Content-Type" = "application/json"
    "Authorization" = "Bearer $Token"
  }
  $Json = $BodyObj | ConvertTo-Json -Depth 6
  return Invoke-RestMethod -Uri $Url -Method PATCH -Headers $Headers -Body $Json
}

Write-Host "=== Preparando usuario y actividades para prueba de carga (Proveedor Legacy) ===" -ForegroundColor Cyan
$Username = "loadtest_" + [Guid]::NewGuid().ToString("N").Substring(0, 6)
$Password = "LoadTest123!"

Invoke-JsonPost "$BaseUrl/api/auth/register" @{ username = $Username; password = $Password } | Out-Null
$LoginResp = Invoke-JsonPost "$BaseUrl/api/auth/login" @{ username = $Username; password = $Password }
$Token = $LoginResp.token

Invoke-JsonPatch "$BaseUrl/api/usuarios/me/proveedor-clima" @{ proveedorClima = "LEGACY" } $Token | Out-Null
Write-Host "Usuario '$Username' configurado con proveedorClima = LEGACY" -ForegroundColor Green

$AdminToken = $null
try {
  $AdminLogin = Invoke-JsonPost "$BaseUrl/api/auth/login" @{ username = $AdminUser; password = $AdminPass }
  $AdminToken = $AdminLogin.token
} catch {
  Write-Host "Aviso: No se pudo autenticar admin para leer /api/admin/estadisticas ($($_.Exception.Message))" -ForegroundColor Yellow
}

$ActividadesIds = @()
$Coordenadas = @(
  @{ ciudad = "Palermo"; lat = -34.58; lon = -58.43; horas = 24 },
  @{ ciudad = "Belgrano"; lat = -34.56; lon = -58.46; horas = 26 },
  @{ ciudad = "Recoleta"; lat = -34.59; lon = -58.39; horas = 28 },
  @{ ciudad = "Caballito"; lat = -34.62; lon = -58.44; horas = 30 },
  @{ ciudad = "San Telmo"; lat = -34.62; lon = -58.37; horas = 32 }
)

foreach ($c in $Coordenadas) {
  $FechaFutura = (Get-Date).AddHours($c.horas).ToString("yyyy-MM-ddTHH:mm:ss")
  $Act = Invoke-JsonPost "$BaseUrl/api/actividades" @{
    titulo = "LoadTest $($c.ciudad)"
    descripcion = "Actividad para prueba de carga de clima legacy"
    tipoActividad = "AIRE_LIBRE"
    ubicacion = @{
      ciudad = $c.ciudad
      latitud = $c.lat
      longitud = $c.lon
    }
    fecha = $FechaFutura
    duracionEstimada = 2
    cantidadMinima = 2
    cantidadMaxima = 10
  } $Token
  $ActividadesIds += $Act.id
}

function Run-Scenario($NombreEscenario) {
  Write-Host "`n=== Ejecutando $NombreEscenario ($RequestsPerScenario requests) ===" -ForegroundColor Cyan
  $Latencias = New-Object System.Collections.Generic.List[double]
  $Exitos200 = 0
  $ErroresHttp = 0
  $ServidosPorLegacy = 0
  $ServidosPorFallback = 0

  for ($i = 0; $i -lt $RequestsPerScenario; $i++) {
    $ActId = $ActividadesIds[$i % $ActividadesIds.Count]
    $Sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
      $Resp = Invoke-RestMethod -Uri "$BaseUrl/api/actividades/$ActId/clima" -Method GET -Headers @{ Authorization = "Bearer $Token" }
      $Sw.Stop()
      $Latencias.Add($Sw.Elapsed.TotalMilliseconds)
      $Exitos200++
      if ($Resp.fallbackActivo) {
        $ServidosPorFallback++
      } elseif ($Resp.proveedorUtilizado -eq "LEGACY") {
        $ServidosPorLegacy++
      }
    } catch {
      $Sw.Stop()
      $Latencias.Add($Sw.Elapsed.TotalMilliseconds)
      $ErroresHttp++
    }
  }

  $Ordenadas = $Latencias | Sort-Object
  $Promedio = [Math]::Round(($Ordenadas | Measure-Object -Average).Average, 2)
  $P50 = [Math]::Round($Ordenadas[[Math]::Floor($Ordenadas.Count * 0.50)], 2)
  $P95 = [Math]::Round($Ordenadas[[Math]::Min($Ordenadas.Count - 1, [Math]::Floor($Ordenadas.Count * 0.95))], 2)
  $Max = [Math]::Round($Ordenadas[$Ordenadas.Count - 1], 2)

  $Resultado = [PSCustomObject]@{
    Escenario = $NombreEscenario
    RequestsTotales = $RequestsPerScenario
    Respuestas200 = $Exitos200
    ErroresHttp = $ErroresHttp
    ResueltosPorLegacy = $ServidosPorLegacy
    ResueltosPorFallback = $ServidosPorFallback
    LatenciaPromedioMs = $Promedio
    LatenciaP50Ms = $P50
    LatenciaP95Ms = $P95
    LatenciaMaxMs = $Max
  }

  $Resultado | Format-List

  if ($AdminToken) {
    $Stats = Invoke-RestMethod -Uri "$BaseUrl/api/admin/estadisticas" -Method GET -Headers @{ Authorization = "Bearer $AdminToken" }
    Write-Host "--- Estado de Proveedores en /api/admin/estadisticas ---" -ForegroundColor Yellow
    $Stats.proveedoresClima | Select-Object proveedor, estadoActual, estadoCircuitBreaker, llamadasTotales, llamadasExitosas, erroresTotales, respuestasInvalidas, timeouts, fallbacksActivados, cacheHits, cacheMisses, cacheHitRatio | Format-Table -AutoSize
  }
}

Run-Scenario "Escenario 1: Servicio Legacy ENCENDIDO (con fallas inyectadas por el binario)"

Write-Host "`nDeteniendo contenedor clima-legacy para simular caida total..." -ForegroundColor Magenta
docker compose stop clima-legacy | Out-Null

# Creamos nuevas actividades con coordenadas distintas para forzar cache misses con el servicio caido
$ActividadesCaidoIds = @()
for ($k = 1; $k -le 5; $k++) {
  $FechaFutura = (Get-Date).AddHours(36 + $k).ToString("yyyy-MM-ddTHH:mm:ss")
  $Act = Invoke-JsonPost "$BaseUrl/api/actividades" @{
    titulo = "LoadTest Caido $k"
    descripcion = "Actividad para escenario legacy caido"
    tipoActividad = "AIRE_LIBRE"
    ubicacion = @{
      ciudad = "Zona $k"
      latitud = [Math]::Round(-34.50 - ($k * 0.03), 2)
      longitud = [Math]::Round(-58.40 - ($k * 0.03), 2)
    }
    fecha = $FechaFutura
    duracionEstimada = 2
    cantidadMinima = 2
    cantidadMaxima = 10
  } $Token
  $ActividadesCaidoIds += $Act.id
}
$ActividadesIds = $ActividadesCaidoIds

try {
  Run-Scenario "Escenario 2: Servicio Legacy CAIDO (Circuit Breaker + Fallback a WeatherAPI)"
} finally {
  Write-Host "`nRestaurando contenedor clima-legacy..." -ForegroundColor Cyan
  docker compose start clima-legacy | Out-Null
}
