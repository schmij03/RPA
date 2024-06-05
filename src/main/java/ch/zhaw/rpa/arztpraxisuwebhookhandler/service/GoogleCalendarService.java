package ch.zhaw.rpa.arztpraxisuwebhookhandler.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class GoogleCalendarService {

    @Value("${google.api.key}")
    private String apiKey;

    @Autowired
    private RestTemplate restTemplate;

    public List<Map<String, Object>> getCalendarEvents(String calendarId) {
        String url = String.format("https://www.googleapis.com/calendar/v3/calendars/%s/events?key=%s", calendarId, apiKey);
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        return (List<Map<String, Object>>) response.get("items");
    }

    public List<String> findFreeTimeSlots(String calendarId, Date date) {
        List<Map<String, Object>> events = getCalendarEvents(calendarId);
        List<String> freeSlots = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

        String dateString = dateFormat.format(date);

        List<String> workPeriods = Arrays.asList(
                dateString + "T08:00:00",
                dateString + "T12:00:00",
                dateString + "T13:00:00",
                dateString + "T17:00:00"
        );

        for (int i = 0; i < workPeriods.size(); i += 2) {
            String startPeriod = workPeriods.get(i);
            String endPeriod = workPeriods.get(i + 1);
            boolean isFree = true;

            for (Map<String, Object> event : events) {
                String start = (String) ((Map<String, Object>) event.get("start")).get("dateTime");
                String end = (String) ((Map<String, Object>) event.get("end")).get("dateTime");

                if ((start.compareTo(startPeriod) < 0 && end.compareTo(startPeriod) > 0) || (start.compareTo(endPeriod) < 0 && end.compareTo(endPeriod) > 0)) {
                    isFree = false;
                    break;
                }
            }

            if (isFree) {
                freeSlots.add(startPeriod + " to " + endPeriod);
            }
        }

        return freeSlots;
    }
}
