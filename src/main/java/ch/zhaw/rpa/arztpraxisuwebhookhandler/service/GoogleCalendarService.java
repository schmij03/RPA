package ch.zhaw.rpa.arztpraxisuwebhookhandler.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class GoogleCalendarService {

    @Value("${google.api.credentials.filepath}")
    private String credentialsFilePath;

    @Value("${google.api.impersonated.user.email}")
    private String impersonatedUserEmail;

    private static final String APPLICATION_NAME = "Google Calendar API Java Quickstart";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private Calendar getCalendarService() throws GeneralSecurityException, IOException {
        GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(credentialsFilePath))
                .createScoped(Collections.singleton(CalendarScopes.CALENDAR))
                .createDelegated(impersonatedUserEmail);
        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

        return new Calendar.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, requestInitializer)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public List<String> findFreeTimeSlots(String calendarId, Date startDate, int countDays) {
        try {
            Calendar service = getCalendarService();
            SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
            dateTimeFormat.setTimeZone(TimeZone.getTimeZone("Europe/Zurich"));

            java.util.Calendar calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("Europe/Zurich"));
            calendar.setTime(startDate);

            String timeMin = dateTimeFormat.format(startDate);
            calendar.add(java.util.Calendar.DAY_OF_MONTH, countDays);
            String timeMax = dateTimeFormat.format(calendar.getTime());

            FreeBusyRequest request = new FreeBusyRequest()
                    .setTimeMin(new com.google.api.client.util.DateTime(timeMin))
                    .setTimeMax(new com.google.api.client.util.DateTime(timeMax))
                    .setItems(Collections.singletonList(new FreeBusyRequestItem().setId(calendarId)));

            FreeBusyResponse response = service.freebusy().query(request).execute();
            List<TimePeriod> busyTimes = response.getCalendars().get(calendarId).getBusy();

            List<String> freeSlots = getFreeSlots(startDate, countDays, busyTimes);
            return formatFreeSlots(freeSlots);
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private List<String> getFreeSlots(Date startDate, int countDays, List<TimePeriod> busyTimes) {
        List<String> freeSlots = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
        dateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Zurich"));
        timeFormat.setTimeZone(TimeZone.getTimeZone("Europe/Zurich"));

        java.util.Calendar calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("Europe/Zurich"));
        calendar.setTime(startDate);

        for (int day = 0; day < countDays; day++) {
            int dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK);
            if (dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY) {
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1);
                continue;
            }

            String dateString = dateFormat.format(calendar.getTime());
            String morningStart = dateString + " 08:00";
            String noonEnd = dateString + " 12:00";
            String afternoonStart = dateString + " 13:00";
            String eveningEnd = dateString + " 17:00";

            findPartialFreeSlots(morningStart, noonEnd, busyTimes, freeSlots);
            findPartialFreeSlots(afternoonStart, eveningEnd, busyTimes, freeSlots);

            calendar.add(java.util.Calendar.DAY_OF_MONTH, 1);
        }

        return freeSlots;
    }

    private List<String> formatFreeSlots(List<String> freeSlots) {
        List<String> formattedSlots = new ArrayList<>();
        SimpleDateFormat inputFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");

        for (String slot : freeSlots) {
            try {
                String[] times = slot.split(" bis ");
                Date startDate = inputFormat.parse(times[0]);
                Date endDate = inputFormat.parse(times[1]);

                String formattedSlot = dateFormat.format(startDate) + " " +
                    timeFormat.format(startDate) + " bis " + 
                    timeFormat.format(endDate);
                formattedSlots.add(formattedSlot);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        return formattedSlots;
    }

    private void findPartialFreeSlots(String startPeriod, String endPeriod, List<TimePeriod> busyTimes, List<String> freeSlots) {
        try {
            SimpleDateFormat periodFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
            periodFormat.setTimeZone(TimeZone.getTimeZone("Europe/Zurich"));

            Date periodStart = periodFormat.parse(startPeriod);
            Date periodEnd = periodFormat.parse(endPeriod);

            for (TimePeriod busyTime : busyTimes) {
                Date busyStart = new Date(busyTime.getStart().getValue());
                Date busyEnd = new Date(busyTime.getEnd().getValue());

                if (busyStart.before(periodEnd) && busyEnd.after(periodStart)) {
                    // Adjust start and end times based on busy period
                    if (busyStart.after(periodStart)) {
                        freeSlots.add(periodFormat.format(periodStart) + " bis " + periodFormat.format(busyStart));
                    }
                    periodStart = busyEnd;
                }
            }

            if (periodStart.before(periodEnd)) {
                freeSlots.add(periodFormat.format(periodStart) + " bis " + periodFormat.format(periodEnd));
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public String validateAndCreateEvent(String dateTimeStr, String participantEmail, String name) {
        System.out.println("Creating event for: " + dateTimeStr);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX"); // Anpassung an das ISO 8601 Format mit Zeitzone
        format.setLenient(false); // Stellen Sie sicher, dass das Datum genau dem Format entsprechen muss
        try {
            Date date = format.parse(dateTimeStr);
            return createCalendarEvent(date, participantEmail, name);
        } catch (ParseException e) {
            return "Ungültiges Datumsformat. Bitte geben Sie das Datum im Format 'yyyy-MM-dd'T'HH:mm:ssXXX' erneut ein. Beispiel: 2022-01-01T14:30:00+01:00";
        }
    }

    public String createCalendarEvent(Date startDateTime, String participantEmail, String name) {
        try {
            Calendar service = getCalendarService();
            Event event = new Event()
                .setSummary("Arzttermin: "+name)
                .setDescription("Termin über Dialogflow erstellt.")
                .setAttendees(Collections.singletonList(new EventAttendee().setEmail(participantEmail))); // Teilnehmer hinzufügen

            DateTime start = new DateTime(startDateTime);
            EventDateTime startEventDateTime = new EventDateTime().setDateTime(start);
            event.setStart(startEventDateTime);

            DateTime end = new DateTime(startDateTime.getTime() + 1800000);
            EventDateTime endEventDateTime = new EventDateTime().setDateTime(end);
            event.setEnd(endEventDateTime);

            // Zum Standardkalender hinzufügen
            event = service.events().insert("rpaarztpraxis@gmail.com", event).execute();

            return "Termin erfolgreich für " + new SimpleDateFormat("dd.MM.yyyy 'um' HH:mm 'Uhr'").format(startDateTime) + " erstellt. \n \n Wenn Sie eine Terminbestätigung wünschen schreiben Sie bitte 'Bestätigung'.";
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            return "Fehler beim Erstellen des Termins: " + e.getMessage();
        }
    }
}
