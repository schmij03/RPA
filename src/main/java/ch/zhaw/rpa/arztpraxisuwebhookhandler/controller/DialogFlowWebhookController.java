package ch.zhaw.rpa.arztpraxisuwebhookhandler.controller;

import java.io.IOException;
import java.io.StringWriter;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.client.json.JsonGenerator;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2IntentMessage;
import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2IntentMessageText;
import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2QueryResult;
import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2WebhookRequest;
import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2WebhookResponse;

import ch.zhaw.rpa.arztpraxisuwebhookhandler.handler.UiPathHandler;
import ch.zhaw.rpa.arztpraxisuwebhookhandler.service.GoogleCalendarService; // Import the missing class

@RestController
@RequestMapping(value = "api")
public class DialogFlowWebhookController {

    @Autowired
    private GsonFactory gsonFactory;

    @Autowired
    private UiPathHandler uiPathHandler;

    @Autowired
    private GoogleCalendarService googleCalendarService;

    @GetMapping(value = "/test")
    public String testApi() {
        System.out.println("!!!!!!!!! Test Request received");
        return "Yes, it works";
    }

@PostMapping(value = "/dialogflow-main-handler", produces = { MediaType.APPLICATION_JSON_VALUE })
public String webhook(@RequestBody String rawData) throws IOException, GeneralSecurityException {
    GoogleCloudDialogflowV2WebhookResponse response = new GoogleCloudDialogflowV2WebhookResponse();
    GoogleCloudDialogflowV2IntentMessage msg = new GoogleCloudDialogflowV2IntentMessage();
    GoogleCloudDialogflowV2WebhookRequest request = gsonFactory
            .createJsonParser(rawData)
            .parse(GoogleCloudDialogflowV2WebhookRequest.class);
    GoogleCloudDialogflowV2QueryResult queryResult = request.getQueryResult();
    
    if (queryResult == null || queryResult.getIntent() == null || queryResult.getIntent().getDisplayName() == null) {
        GoogleCloudDialogflowV2IntentMessageText text = new GoogleCloudDialogflowV2IntentMessageText();
        text.setText(List.of("Invalid request data"));
        msg.setText(text);
        response.setFulfillmentMessages(List.of(msg));
        StringWriter stringWriter = new StringWriter();
        JsonGenerator jsonGenerator = gsonFactory.createJsonGenerator(stringWriter);
        jsonGenerator.enablePrettyPrint();
        jsonGenerator.serialize(response);
        jsonGenerator.flush();
        return stringWriter.toString();
    }
    
    String intent = queryResult.getIntent().getDisplayName();
    Map<String, Object> parameters = queryResult.getParameters();

    // Je nach Intent anderen Handler aufrufen oder Response zusammenbauen
    if ("PatientRegistrieren".equals(intent)) {
        // Patientendaten erfassen
        String vorname = getParameterString(parameters, "vorname");
        String nachname = getParameterString(parameters, "nachname");
        String ahvNumber = getParameterString(parameters, "ahvNumber");
        String email = getParameterString(parameters, "email");
        String handynummer = getParameterString(parameters, "handynummer");
        
        msg = uiPathHandler.handlePatientRegistration(request, vorname, nachname, ahvNumber, email, handynummer, msg);
    } else if ("TerminVereinbaren".equals(intent)) {
        String calendarId = "rpaarztpraxis@gmail.com"; // Use your calendar ID
        Date date = new Date();
        int d = 7;

        List<String> freeSlots = googleCalendarService.findFreeTimeSlots(calendarId, date, d);
        System.out.println("Free slots: " + freeSlots); // Print to console
        msg.setText(new GoogleCloudDialogflowV2IntentMessageText().setText(List.of("Freie Termine:\n"+String.join(", ", freeSlots))));
    }
    else if ("TerminAuswählen".equals(intent)) {
        String termin = getParameterString(parameters, "termin");
        msg = uiPathHandler.handleAppointmentSelection(request, termin, msg);
    } else if ("ContinuePatientIntent".equals(intent)) {
        msg = uiPathHandler.handleContinueRequestForPatient(request, msg);
    } else if ("ContinueTerminIntent".equals(intent)) {
        msg = uiPathHandler.handleContinueRequestForAppointment(request, msg);
    } else {
        // Response no handler found zusammenstellen
        GoogleCloudDialogflowV2IntentMessageText text = new GoogleCloudDialogflowV2IntentMessageText();
        text.setText(List.of("There's no handler for '" + intent + "'"));
        msg.setText(text);
    }

    // Webhook-Response für Dialogflow aufbereiten und zurückgeben
    response.setFulfillmentMessages(List.of(msg));
    StringWriter stringWriter = new StringWriter();
    JsonGenerator jsonGenerator = gsonFactory.createJsonGenerator(stringWriter);
    jsonGenerator.enablePrettyPrint();
    jsonGenerator.serialize(response);
    jsonGenerator.flush();
    return stringWriter.toString();
}

private String getParameterString(Map<String, Object> parameters, String key) {
    Object value = parameters.get(key);
    return value != null ? value.toString() : "";
}

}
