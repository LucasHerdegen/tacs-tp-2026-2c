import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;

public class TestDate {
    public static void main(String[] args) {
        DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime hora = LocalDateTime.parse("2026-09-28 15:00", FORMATO_HORA);
        // MongoDB saved: ISODate('2026-09-28T15:00:00.000Z')
        // In UTC this is 15:00. In Argentina this is 12:00.
        // Spring's LocalDateTime doesn't have timezone. 
        // If MongoDB deserializes it as java.util.Date and then converts to LocalDateTime using system default zone (Argentina),
        // it would become 12:00!
        // Let's print something.
        System.out.println("Done");
    }
}
