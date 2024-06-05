package ch.zhaw.rpa.arztpraxisuwebhookhandler.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.FreeBusyRequest;
import com.google.api.services.calendar.model.FreeBusyRequestItem;
import com.google.api.services.calendar.model.FreeBusyResponse;
import com.google.api.services.calendar.model.TimePeriod;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class GoogleCalendarService {

    @Value("${google.api.credentials.filepath}")
    private String credentialsFilePath;

    private static final String APPLICATION_NAME = "Google Calendar API Java Quickstart";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private Calendar getCalendarService() throws GeneralSecurityException, IOException {
        GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(credentialsFilePath))
            .createScoped(Collections.singleton(CalendarScopes.CALENDAR));
        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

        return new Calendar.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, requestInitializer)
            .setApplicationName(APPLICATION_NAME)
            .build();
    }

    public List<String> findFreeTimeSlots(String calendarId, Date startDate, int countDays) {
        try {
            Calendar service = getCalendarService();
            SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
            java.util.Calendar calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"));
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

            return getFreeSlots(startDate, countDays, busyTimes);
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private List<String> getFreeSlots(Date startDate, int countDays, List<TimePeriod> busyTimes) {
        List<String> freeSlots = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
        java.util.Calendar calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTime(startDate);

        for (int day = 0; day < countDays; day++) {
            int dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK);
            if (dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY) {
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1);
                continue;
            }

            String dateString = dateFormat.format(calendar.getTime());
            List<String> workPeriods = Arrays.asList(
                    dateString + " " + "08:00",
                    dateString + " " + "12:00",
                    dateString + " " + "13:00",
                    dateString + " " + "17:00"
            );

            for (int i = 0; i < workPeriods.size(); i += 2) {
                String startPeriod = workPeriods.get(i);
                String endPeriod = workPeriods.get(i + 1);
                boolean isFree = isPeriodFree(startPeriod, endPeriod, busyTimes, dateFormat, timeFormat);

                if (isFree) {
                    freeSlots.add(startPeriod + " bis " + endPeriod.split(" ")[1]);
                }
            }

            calendar.add(java.util.Calendar.DAY_OF_MONTH, 1);
        }

        return freeSlots;
    }

    private boolean isPeriodFree(String startPeriod, String endPeriod, List<TimePeriod> busyTimes, SimpleDateFormat dateFormat, SimpleDateFormat timeFormat) {
        try {
            Date start = new SimpleDateFormat("dd.MM.yyyy HH:mm").parse(startPeriod);
            Date end = new SimpleDateFormat("dd.MM.yyyy HH:mm").parse(endPeriod);
            for (TimePeriod busyTime : busyTimes) {
                Date busyStart = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").parse(busyTime.getStart().toString());
                Date busyEnd = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").parse(busyTime.getEnd().toString());
                if ((start.before(busyEnd) && end.after(busyStart)) || start.equals(busyStart) || end.equals(busyEnd)) {
                    return false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }
}
