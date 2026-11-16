param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$PrometheusUrl = "http://localhost:9090",
  [string]$JaegerUrl = "http://localhost:16686",
  [int]$SeedCount = 300,
  [int]$DuracionEscenarioSegundos = 25,
  [int]$ConcurrenciaBaja = 2,
  [int]$ConcurrenciaAlta = 24,
  [int]$PausaEntreEscenariosSegundos = 15
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http

function Invoke-JsonPost($Url, $BodyObj, $Token = $null) {
  $Headers = @{}
  if ($Token) { $Headers["Authorization"] = "Bearer $Token" }
  $Json = $BodyObj | ConvertTo-Json -Depth 6 -Compress
  $BodyBytes = [System.Text.Encoding]::UTF8.GetBytes($Json)
  try {
    return Invoke-RestMethod -Uri $Url -Method POST -Headers $Headers -ContentType "application/json; charset=utf-8" -Body $BodyBytes
  } catch {
    if ($_.Exception.Response) {
      $Reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
      $Detail = $Reader.ReadToEnd()
      throw "POST $Url fallo: $($_.Exception.Message) -> $Detail"
    }
    throw
  }
}

function Get-Percentil($ListaOrdenada, [double]$Percentil) {
  if (-not $ListaOrdenada -or $ListaOrdenada.Count -eq 0) { return 0.0 }
  $Indice = [Math]::Ceiling($Percentil * $ListaOrdenada.Count) - 1
  if ($Indice -lt 0) { $Indice = 0 }
  if ($Indice -ge $ListaOrdenada.Count) { $Indice = $ListaOrdenada.Count - 1 }
  return [Math]::Round($ListaOrdenada[$Indice], 2)
}

Write-Host "=== 1. Autenticación y verificación de volumen de datos ===" -ForegroundColor Cyan
$Username = "obsload_" + [Guid]::NewGuid().ToString("N").Substring(0, 6)
$Password = "LoadTest123!"

Invoke-JsonPost "$BaseUrl/api/auth/register" @{ username = $Username; password = $Password } | Out-Null
$LoginResp = Invoke-JsonPost "$BaseUrl/api/auth/login" @{ username = $Username; password = $Password }
$Token = $LoginResp.token
$AuthHeaders = @{ Authorization = "Bearer $Token" }

$PaginaActual = Invoke-RestMethod -Uri "$BaseUrl/api/actividades?page=0&size=1" -Method GET -Headers $AuthHeaders
$Existentes = [int]$PaginaActual.totalElements
$Faltantes = [Math]::Max(0, $SeedCount - $Existentes)

Write-Host "Actividades existentes en MongoDB: $Existentes (objetivo mínimo: $SeedCount, a crear: $Faltantes)" -ForegroundColor Gray

$Ciudades = @("Palermo", "Belgrano", "Recoleta", "Caballito", "San Telmo", "Villa Crespo", "Nunez", "Almagro")
$Tipos = @("AIRE_LIBRE", "TECHADA", "MIXTA")

for ($i = 0; $i -lt $Faltantes; $i++) {
  $Ciudad = $Ciudades[$i % $Ciudades.Count]
  $Tipo = $Tipos[$i % $Tipos.Count]
  $FechaFutura = (Get-Date).AddDays(2 + ($i % 10)).AddHours($i % 12).ToString("yyyy-MM-ddTHH:mm:ss")

  Invoke-JsonPost "$BaseUrl/api/actividades" @{
    titulo = "Encuentro $Tipo $Ciudad #$i"
    descripcion = "Actividad de prueba de observabilidad y carga en $Ciudad"
    tipoActividad = $Tipo
    ubicacion = @{
      ciudad = $Ciudad
      latitud = -34.60 + (($i % 10) * 0.005)
      longitud = -58.42 + (($i % 10) * 0.005)
    }
    fecha = $FechaFutura
    duracionEstimada = 120
    cantidadMinima = 2
    cantidadMaxima = 20
  } $Token | Out-Null
}

$TotalDespuesSeed = (Invoke-RestMethod -Uri "$BaseUrl/api/actividades?page=0&size=1" -Method GET -Headers $AuthHeaders).totalElements
Write-Host "Volumen final disponible para búsqueda: $TotalDespuesSeed actividades" -ForegroundColor Green

$ConsultasValidas = @(
  "/api/actividades?page=0&size=20",
  "/api/actividades?tipo=AIRE_LIBRE&page=0&size=20",
  "/api/actividades?busqueda=Palermo&page=0&size=20",
  "/api/actividades?busqueda=Encuentro&tipo=TECHADA&page=0&size=20",
  "/api/actividades?estado=PROPUESTA&page=1&size=15",
  "/api/actividades?tipo=MIXTA&cupoDisponible=true&page=0&size=20"
)

function Invoke-EscenarioCarga([string]$Nombre, [int]$Concurrencia, [int]$DuracionSeg) {
  Write-Host "`n=== Ejecutando $Nombre (concurrencia=$Concurrencia, duracion=${DuracionSeg}s) ===" -ForegroundColor Yellow
  $InicioUtc = [DateTimeOffset]::UtcNow
  $DeadlineUtc = $InicioUtc.AddSeconds($DuracionSeg)

  $Pool = [RunspaceFactory]::CreateRunspacePool(1, $Concurrencia)
  $Pool.Open()

  $ScriptWorker = {
    param($UrlBase, $BearerToken, $Rutas, $DeadlineTicks, $WorkerId)
    $Handler = New-Object System.Net.Http.HttpClientHandler
    $Client = New-Object System.Net.Http.HttpClient($Handler)
    $Client.Timeout = [TimeSpan]::FromSeconds(15)
    $Client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $BearerToken)

    $Resultados = New-Object System.Collections.Generic.List[object]
    $Idx = $WorkerId
    while ([DateTimeOffset]::UtcNow.UtcTicks -lt $DeadlineTicks) {
      $Ruta = $Rutas[$Idx % $Rutas.Count]
      $Idx++
      $Sw = [System.Diagnostics.Stopwatch]::StartNew()
      $Status = 0
      try {
        $Resp = $Client.GetAsync("$UrlBase$Ruta").GetAwaiter().GetResult()
        $null = $Resp.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        $Status = [int]$Resp.StatusCode
      } catch {
        $Status = 0
      } finally {
        $Sw.Stop()
      }
      $Resultados.Add([PSCustomObject]@{
        Status = $Status
        Ms = $Sw.Elapsed.TotalMilliseconds
      })
    }
    $Client.Dispose()
    return $Resultados
  }

  $Tareas = @()
  for ($w = 0; $w -lt $Concurrencia; $w++) {
    $Ps = [PowerShell]::Create()
    $Ps.RunspacePool = $Pool
    $null = $Ps.AddScript($ScriptWorker).AddArgument($BaseUrl).AddArgument($Token).AddArgument($ConsultasValidas).AddArgument($DeadlineUtc.UtcTicks).AddArgument($w)
    $Handle = $Ps.BeginInvoke()
    $Tareas += [PSCustomObject]@{ PowerShell = $Ps; Handle = $Handle }
  }

  $Muestras = New-Object System.Collections.Generic.List[object]
  foreach ($t in $Tareas) {
    $Salida = $t.PowerShell.EndInvoke($t.Handle)
    foreach ($item in $Salida) { $Muestras.Add($item) }
    $t.PowerShell.Dispose()
  }
  $Pool.Close()
  $Pool.Dispose()

  $FinUtc = [DateTimeOffset]::UtcNow
  $DuracionReal = [Math]::Max(0.001, ($FinUtc - $InicioUtc).TotalSeconds)

  $Exitos = ($Muestras | Where-Object { $_.Status -ge 200 -and $_.Status -lt 300 }).Count
  $Fallos = $Muestras.Count - $Exitos
  $Latencias = $Muestras | ForEach-Object { [double]$_.Ms } | Sort-Object

  $Promedio = 0.0
  if ($Latencias.Count -gt 0) {
    $Promedio = [Math]::Round(($Latencias | Measure-Object -Average).Average, 2)
  }

  $Resumen = [PSCustomObject]@{
    Escenario = $Nombre
    Concurrencia = $Concurrencia
    InicioIso = $InicioUtc.ToString("yyyy-MM-ddTHH:mm:ssZ")
    FinIso = $FinUtc.ToString("yyyy-MM-ddTHH:mm:ssZ")
    InicioEpoch = $InicioUtc.ToUnixTimeSeconds()
    FinEpoch = $FinUtc.ToUnixTimeSeconds()
    DuracionSeg = [Math]::Round($DuracionReal, 2)
    TotalRequests = $Muestras.Count
    Exitos2xx = $Exitos
    Errores = $Fallos
    PorcentajeError = if ($Muestras.Count -gt 0) { [Math]::Round(100.0 * $Fallos / $Muestras.Count, 2) } else { 0.0 }
    ThroughputReqSeg = [Math]::Round($Muestras.Count / $DuracionReal, 2)
    LatenciaAvgMs = $Promedio
    LatenciaP50Ms = Get-Percentil $Latencias 0.50
    LatenciaP95Ms = Get-Percentil $Latencias 0.95
    LatenciaMaxMs = if ($Latencias.Count -gt 0) { [Math]::Round($Latencias[-1], 2) } else { 0.0 }
  }

  $Resumen | Format-List | Out-Host
  return $Resumen
}

function Show-WindowTraces($Label, [long]$StartEpoch, [long]$EndEpoch) {
  try {
    $StartUs = $StartEpoch * 1000000
    $EndUs = $EndEpoch * 1000000
    $Resp = Invoke-RestMethod -Uri "$JaegerUrl/api/traces?service=tacs-backend&operation=actividad.buscar&start=$StartUs&end=$EndUs&limit=5" -Method GET
    if (-not $Resp.data -or $Resp.data.Count -eq 0) {
      Write-Host "$Label : sin trazas devueltas" -ForegroundColor DarkGray
      return
    }
    $Ranked = $Resp.data | ForEach-Object {
      $Root = $_.spans | Where-Object { $_.operationName -eq "GET /api/actividades" } | Select-Object -First 1
      [PSCustomObject]@{ Trace = $_; HttpMs = if ($Root) { $Root.duration / 1000.0 } else { 0.0 } }
    } | Sort-Object HttpMs
    $Mid = $Ranked[[int]($Ranked.Count / 2)]
    $Slow = $Ranked[-1]
    foreach ($Entry in @(@{ Tag = "mediana"; Item = $Mid }, @{ Tag = "lenta"; Item = $Slow })) {
      $T = $Entry.Item.Trace
      $HttpSpan = ($T.spans | Where-Object { $_.operationName -eq "GET /api/actividades" } | Select-Object -First 1)
      $BusqSpan = ($T.spans | Where-Object { $_.operationName -eq "actividad.buscar" } | Select-Object -First 1)
      $DbSpan = ($T.spans | Where-Object { $_.operationName -eq "actividad.buscar.db" } | Select-Object -First 1)
      $JwtSpan = ($T.spans | Where-Object { $_.operationName -eq "UsuarioMongoRepository.findById" } | Select-Object -First 1)
      $AggSpan = ($T.spans | Where-Object { $_.operationName -eq "aggregate actividades" } | Select-Object -First 1)
      $FindActSpan = ($T.spans | Where-Object { $_.operationName -eq "find actividades" } | Select-Object -First 1)
      $FindUsrCount = ($T.spans | Where-Object { $_.operationName -eq "find usuarios" }).Count
      $Fmt = { param($sp) if ($sp) { "$([Math]::Round($sp.duration/1000.0, 2))ms" } else { "N/D" } }
      Write-Host ("  [{0} - {1}] traceID={2} (spans={3}) | HTTP={4} | JWT findById={5} | actividad.buscar={6} | actividad.buscar.db={7} (aggregate={8}, find={9}, find usuarios x{10})" -f
        $Label, $Entry.Tag, $T.traceID, $T.spans.Count,
        (& $Fmt $HttpSpan), (& $Fmt $JwtSpan), (& $Fmt $BusqSpan), (& $Fmt $DbSpan),
        (& $Fmt $AggSpan), (& $Fmt $FindActSpan), $FindUsrCount)
    }
  } catch {
    Write-Host "No se pudo consultar Jaeger para $Label : $($_.Exception.Message)" -ForegroundColor DarkGray
  }
}

Write-Host "`n=== 2. Demostración aislada de ejecución fallida controlada (HTTP 400) ===" -ForegroundColor Magenta
$InicioError = [DateTimeOffset]::UtcNow
try {
  $null = Invoke-RestMethod -Uri "$BaseUrl/api/actividades?fechaInicio=2026-12-31&fechaFin=2026-01-01&page=0&size=20" -Method GET -Headers $AuthHeaders
} catch {
  Write-Host "Fallo controlado capturado correctamente (fechaInicio > fechaFin -> HTTP 400): $($_.Exception.Message)" -ForegroundColor DarkYellow
}
$FinError = [DateTimeOffset]::UtcNow
Start-Sleep -Seconds 6

$ErrTraceId = $null
try {
  $StartUs = ($InicioError.ToUnixTimeMilliseconds() - 2000) * 1000
  $EndUs = ($FinError.ToUnixTimeMilliseconds() + 5000) * 1000
  $ErrTraces = Invoke-RestMethod -Uri "$JaegerUrl/api/traces?service=tacs-backend&start=$StartUs&end=$EndUs&limit=10" -Method GET
  $TraceErr = $ErrTraces.data | Where-Object { $_.spans | Where-Object { $_.operationName -eq "actividad.buscar" -and ($_.tags | Where-Object { $_.key -eq "otel.status_code" -and $_.value -eq "ERROR" }) } } | Select-Object -First 1
  if ($TraceErr) {
    $ErrTraceId = $TraceErr.traceID
    Write-Host "Traza fallida verificada en Jaeger traceID: $ErrTraceId" -ForegroundColor Yellow
  }
} catch { }

$Esc1 = Invoke-EscenarioCarga -Nombre "Escenario 1 - Línea base (baja concurrencia)" -Concurrencia $ConcurrenciaBaja -DuracionSeg $DuracionEscenarioSegundos

Write-Host "Esperando 5s para capturar trazas del Escenario 1 y completar ${PausaEntreEscenariosSegundos}s de separación..." -ForegroundColor Cyan
Start-Sleep -Seconds 5
Write-Host "`n--- Muestra Jaeger Escenario 1 ---" -ForegroundColor Green
Show-WindowTraces "Escenario 1" $Esc1.InicioEpoch $Esc1.FinEpoch
$RestantePausa = [Math]::Max(1, $PausaEntreEscenariosSegundos - 5)
Start-Sleep -Seconds $RestantePausa

$Esc2 = Invoke-EscenarioCarga -Nombre "Escenario 2 - Alta concurrencia" -Concurrencia $ConcurrenciaAlta -DuracionSeg $DuracionEscenarioSegundos

Write-Host "Esperando 12s para consolidación final en OTel Collector y scrape de Prometheus..." -ForegroundColor Cyan
Start-Sleep -Seconds 12

function Query-PrometheusInstant([string]$PromQl, [long]$EpochTime) {
  try {
    $Encoded = [Uri]::EscapeDataString($PromQl)
    $Resp = Invoke-RestMethod -Uri "$PrometheusUrl/api/v1/query?query=$Encoded&time=$EpochTime" -Method GET
    if ($Resp.status -eq "success" -and $Resp.data.result.Count -gt 0) {
      return [double]$Resp.data.result[0].value[1]
    }
  } catch { }
  return $null
}

Write-Host "`n=== 3. Resumen comparativo desde Prometheus ===" -ForegroundColor Green
foreach ($Esc in @($Esc1, $Esc2)) {
  $Win = [Math]::Max(15, [int][Math]::Round($Esc.DuracionSeg))
  $T = $Esc.FinEpoch
  $Rps = Query-PrometheusInstant "sum(rate(http_server_request_duration_seconds_count{service_name=`"tacs-backend`",http_route=`"/api/actividades`",http_request_method=`"GET`"}[${Win}s]))" $T
  $ErrRate = Query-PrometheusInstant "100 * ((sum(rate(http_server_request_duration_seconds_count{service_name=`"tacs-backend`",http_route=`"/api/actividades`",http_request_method=`"GET`",http_response_status_code!~`"2..`"}[${Win}s])) or vector(0)) / clamp_min(sum(rate(http_server_request_duration_seconds_count{service_name=`"tacs-backend`",http_route=`"/api/actividades`",http_request_method=`"GET`"}[${Win}s])), 1e-9))" $T
  $P95Http = Query-PrometheusInstant "histogram_quantile(0.95, sum by (le) (rate(http_server_request_duration_seconds_bucket{service_name=`"tacs-backend`",http_route=`"/api/actividades`",http_request_method=`"GET`"}[${Win}s])))" $T
  $Completadas = Query-PrometheusInstant "sum(rate(actividad_busquedas_completadas_total{service_name=`"tacs-backend`"}[${Win}s]))" $T
  $P95Db = Query-PrometheusInstant "histogram_quantile(0.95, sum by (le) (rate(actividad_busqueda_db_duration_seconds_bucket{service_name=`"tacs-backend`",operacion=`"buscar_actividades`"}[${Win}s])))" $T

  Write-Host ("[{0}] Ventana {1} -> {2}" -f $Esc.Escenario, $Esc.InicioIso, $Esc.FinIso) -ForegroundColor Cyan
  Write-Host ("  PromQL req/s: {0} | % error: {1}% | búsquedas completadas/s: {2} | p95 HTTP: {3} ms | p95 DB: {4} ms" -f
    ($(if ($null -ne $Rps) { [Math]::Round($Rps, 2) } else { "N/D" })),
    ($(if ($null -ne $ErrRate) { [Math]::Round($ErrRate, 2) } else { "N/D" })),
    ($(if ($null -ne $Completadas) { [Math]::Round($Completadas, 2) } else { "N/D" })),
    ($(if ($null -ne $P95Http -and -not [double]::IsNaN($P95Http)) { [Math]::Round($P95Http * 1000, 2) } else { "N/D" })),
    ($(if ($null -ne $P95Db -and -not [double]::IsNaN($P95Db)) { [Math]::Round($P95Db * 1000, 2) } else { "N/D" })))
}

Write-Host "`n=== 4. Muestra de trazas en Jaeger (Escenario 2) ===" -ForegroundColor Green
Show-WindowTraces "Escenario 2" $Esc2.InicioEpoch $Esc2.FinEpoch
