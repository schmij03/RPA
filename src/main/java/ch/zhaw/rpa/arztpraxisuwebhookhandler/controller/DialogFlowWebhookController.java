package ch.zhaw.rpa.arztpraxisuwebhookhandler.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;

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

import ch.zhaw.rpa.arztpraxisuwebhookhandler.asynchandling.DialogFlowSessionState;
import ch.zhaw.rpa.arztpraxisuwebhookhandler.asynchandling.DialogFlowSessionStateService;
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
    private DialogFlowSessionStateService stateService;

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

        if (queryResult == null || queryResult.getIntent() == null
                || queryResult.getIntent().getDisplayName() == null) {
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
        System.out.println(intent);
        System.out.println("Parameters: " + parameters);

        // Je nach Intent anderen Handler aufrufen oder Response zusammenbauen
        if ("PatientRegistrieren".equals(intent)) {
            // Patientendaten erfassen
            String vorname = getParameterString(parameters, "vorname");
            String nachname = getParameterString(parameters, "nachname");
            String ahvNumber = getParameterString(parameters, "AHVNumber");
            String email = getParameterString(parameters, "email");
            String handynummer = getParameterString(parameters, "handynummer");
            System.out.println("Handle Patient Registration");
            msg = uiPathHandler.handlePatientRegistration(request, vorname, nachname, ahvNumber, email, handynummer,
                    msg);
        } else if ("TerminVereinbaren".equals(intent)) {
            String calendarId = "rpaarztpraxis@gmail.com"; // Use your calendar ID
            Date date = new Date();
            int d = 7;
            String ahvNumber = getParameterString(parameters, "AHVNumber");

            //Get Request
            try {
                String url = "https://cloud.uipath.com/rpaarztpraxis/DefaultTenant/dataservice_/api/EntityService/PatientRPA/read";
                String bearerToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjhDNzY1N0U2NEExNzNEMTRCNzhEQkIzRjRGQjdEQTJBMDFCNzE1MTEiLCJ4NXQiOiJqSFpYNWtvWFBSUzNqYnNfVDdmYUtnRzNGUkUiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2Nsb3VkLnVpcGF0aC5jb20vaWRlbnRpdHlfIiwibmJmIjoxNzE4NTQwNDU2LCJpYXQiOjE3MTg1NDA3NTYsImV4cCI6MTcxODU0NDM1NiwiYXVkIjoiRGF0YVNlcnZpY2VPcGVuQXBpIiwic2NvcGUiOlsiRGF0YVNlcnZpY2UuRGF0YS5SZWFkIiwiRGF0YVNlcnZpY2UuRGF0YS5Xcml0ZSIsIkRhdGFTZXJ2aWNlLlNjaGVtYS5SZWFkIl0sImFtciI6WyJleHRlcm5hbCJdLCJzdWJfdHlwZSI6InVzZXIiLCJwcnRfaWQiOiI0ZmU1YTY4My04MjMzLTQ4NzAtYmE0NS1lZTZhZTdmNmQzZTgiLCJjbGllbnRfaWQiOiJjODdhMmNkNS03NTYyLTQ3MjgtYjBjYS05N2U3OGE5MTQzMjYiLCJzdWIiOiI0ODkyYzcyYy02M2Q5LTRmOTItOTJlNC1lZDFmZGZiYzBjZDQiLCJhdXRoX3RpbWUiOjE3MTg1MzY3NjMsImlkcCI6Im9pZGMiLCJlbWFpbCI6InJwYWFyenRwcmF4aXNAZ21haWwuY29tIiwiQXNwTmV0LklkZW50aXR5LlNlY3VyaXR5U3RhbXAiOiJJNzRLS1BDN1RPNVNTNzJVWkROVDZBVjRVQVdXSVdPMyIsImF1dGgwX2NvbiI6Imdvb2dsZS1vYXV0aDIiLCJjb3VudHJ5IjoiU3dpdHplcmxhbmQiLCJleHRfc3ViIjoiZ29vZ2xlLW9hdXRoMnwxMDY0ODE0Mzc2NTEzNTUxNzYxOTIiLCJtYXJrZXRpbmdDb25kaXRpb25BY2NlcHRlZCI6IkZhbHNlIiwicGljdHVyZSI6Imh0dHBzOi8vbGgzLmdvb2dsZXVzZXJjb250ZW50LmNvbS9hL0FDZzhvY0pPLWU5TFVGUVVQTjFyYXQ3LTNjOWplSS1uMDNlMTRuUlRNQnJSdHlIOTFlYWI1UT1zOTYtYyIsImhvc3QiOiJGYWxzZSIsImZpcnN0X25hbWUiOiJSUEEiLCJsYXN0X25hbWUiOiJBcnp0cHJheGlzIEFyenRwcmF4aXMiLCJwcnRfYWRtIjoiVHJ1ZSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJycGFhcnp0cHJheGlzQGdtYWlsLmNvbSIsIm5hbWUiOiJycGFhcnp0cHJheGlzQGdtYWlsLmNvbSIsImV4dF9pZHBfaWQiOiIxIiwiZXh0X2lkcF9kaXNwX25hbWUiOiJHbG9iYWxJZHAiLCJzaWQiOiI3NjRCODVFRTQ5RTNGNjA5N0NFRUE5QUFBN0FENzdCQSIsImp0aSI6IjExNkZFQkE2RDFGNDY4OEM3MkU5OEJCNjY3RTRFNDY1In0.FdBXETue8TVi7msMysk2-QywSf_91de9w6T6thPDwvD3kplvV0ACvg23S82W3WxMT1NGmv-0fdV1mXHk_6BCMCvY0cjAmm3BaZdZx9kUPzuTwJq_n6mnL_YUo-DpnNt_ZJgvnBXhnrzvcKnlztIVCy8pJQ96ULFDI2j6vpSbUM4O9S-xuVu4WQnR-mxzJlvwxjTHQg0jbr8-1zXVwX5LpI9Nb8iuEbdF_HV_w5XJAXshUpTxn6dDmCMCt6A6kI947fyvjG7iZzEpL2m-R6C_5TctGyZ9_fSrzM8aeUJIAg2_jJVzXPjSjvIdVaDQCWqUPbzQMQd1sgDZ2SB8sIBMyA";
                String response_API = sendGetRequest(url, bearerToken);
                System.out.println(response_API);
            } catch (Exception e) {
                e.printStackTrace();
            }

            System.out.println("AHV: " + ahvNumber);
            if (!checkAhvNBumber(ahvNumber)) {
                  // Session Id auslesen
                String sessionId = request.getSession();

                // Prüfen, ob Session Id bereits verwaltet ist
                DialogFlowSessionState sessionState = stateService.getSessionStateBySessionId(sessionId);
                GoogleCloudDialogflowV2IntentMessageText text = new GoogleCloudDialogflowV2IntentMessageText();
                text.setText(List.of("Es wurde eine ungültige AHV-Nummer eingegeben. Bitte gib erneut Termin ein. "));
                msg.setText(text);
                stateService.removeSessionState(sessionState);
            } else {

                List<String> freeSlots = googleCalendarService.findFreeTimeSlots(calendarId, date, d);
                msg.setText(new GoogleCloudDialogflowV2IntentMessageText().setText(List.of(
                        "Freie Termine:\n" + String.join(", ", freeSlots)
                                + "\n \nBitte wählen Sie einen Termin aus.")));
            }

        } else if ("TerminAuswählen".equals(intent)) {
            String termin = getParameterString(parameters, "dateTime");
            String ahvNumber = getParameterString(parameters, "ahvNumber");

            String terminString = termin.replace("[{date_time=", "").replace("}]", "");
            String responseMessage = googleCalendarService.validateAndCreateEvent(terminString);
           
            msg.setText(new GoogleCloudDialogflowV2IntentMessageText().setText(List.of(responseMessage)));
            
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

    private boolean checkAhvNBumber(String ahv) {
        // Define the regex pattern for the structure 123.4567.89.12
        String regex = "\\d{3}\\.\\d{4}\\.\\d{4}\\.\\d{2}";

        // Compile the pattern
        Pattern pattern = Pattern.compile(regex);

        // Match the input string against the pattern
        Matcher matcher = pattern.matcher(ahv);

        // Return whether the input string matches the pattern
        return matcher.matches();
    }

    private String getParameterString(Map<String, Object> parameters, String key) {
        Object value = parameters.get(key);
        return value != null ? value.toString() : "";
    }

    public String sendGetRequest(String url, String bearerToken) throws Exception {
        URL obj = new URI(url).toURL();
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        // Setting the request method to GET
        con.setRequestMethod("GET");

        // Adding the Authorization header with the Bearer token
        con.setRequestProperty("Authorization", "Bearer " + bearerToken);

        // Handling the response
        int responseCode = con.getResponseCode();
        System.out.println("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        // Returning the response as a string
        return response.toString();
    }

}
