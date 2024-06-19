package ch.zhaw.rpa.arztpraxisuwebhookhandler.controller;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;
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
import ch.zhaw.rpa.arztpraxisuwebhookhandler.service.GoogleCalendarService;
import ch.zhaw.rpa.arztpraxisuwebhookhandler.service.MongoClientConnection;

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

    public String ahvNumber="";
    public String calendarId = "rpaarztpraxis@gmail.com"; 
    public String getUrl = "https://cloud.uipath.com/rpaarztpraxis/DefaultTenant/dataservice_/api/EntityService/PatientRPA/read";
    public String postUrl = "https://cloud.uipath.com/rpaarztpraxis/DefaultTenant/dataservice_/api/EntityService/PatientRPA/insert";
    public String bearerToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjhDNzY1N0U2NEExNzNEMTRCNzhEQkIzRjRGQjdEQTJBMDFCNzE1MTEiLCJ4NXQiOiJqSFpYNWtvWFBSUzNqYnNfVDdmYUtnRzNGUkUiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2Nsb3VkLnVpcGF0aC5jb20vaWRlbnRpdHlfIiwibmJmIjoxNzE4NzE0MjQxLCJpYXQiOjE3MTg3MTQ1NDEsImV4cCI6MTcxODcxODE0MSwiYXVkIjoiRGF0YVNlcnZpY2VPcGVuQXBpIiwic2NvcGUiOlsiRGF0YVNlcnZpY2UuRGF0YS5SZWFkIiwiRGF0YVNlcnZpY2UuRGF0YS5Xcml0ZSIsIkRhdGFTZXJ2aWNlLlNjaGVtYS5SZWFkIl0sImFtciI6WyJleHRlcm5hbCJdLCJzdWJfdHlwZSI6InVzZXIiLCJwcnRfaWQiOiI0ZmU1YTY4My04MjMzLTQ4NzAtYmE0NS1lZTZhZTdmNmQzZTgiLCJjbGllbnRfaWQiOiJjODdhMmNkNS03NTYyLTQ3MjgtYjBjYS05N2U3OGE5MTQzMjYiLCJzdWIiOiI0ODkyYzcyYy02M2Q5LTRmOTItOTJlNC1lZDFmZGZiYzBjZDQiLCJhdXRoX3RpbWUiOjE3MTg3MTQ1MzgsImlkcCI6Im9pZGMiLCJlbWFpbCI6InJwYWFyenRwcmF4aXNAZ21haWwuY29tIiwiQXNwTmV0LklkZW50aXR5LlNlY3VyaXR5U3RhbXAiOiJJNzRLS1BDN1RPNVNTNzJVWkROVDZBVjRVQVdXSVdPMyIsImF1dGgwX2NvbiI6Imdvb2dsZS1vYXV0aDIiLCJjb3VudHJ5IjoiU3dpdHplcmxhbmQiLCJleHRfc3ViIjoiZ29vZ2xlLW9hdXRoMnwxMDY0ODE0Mzc2NTEzNTUxNzYxOTIiLCJtYXJrZXRpbmdDb25kaXRpb25BY2NlcHRlZCI6IkZhbHNlIiwicGljdHVyZSI6Imh0dHBzOi8vbGgzLmdvb2dsZXVzZXJjb250ZW50LmNvbS9hL0FDZzhvY0pPLWU5TFVGUVVQTjFyYXQ3LTNjOWplSS1uMDNlMTRuUlRNQnJSdHlIOTFlYWI1UT1zOTYtYyIsImhvc3QiOiJGYWxzZSIsImZpcnN0X25hbWUiOiJSUEEiLCJsYXN0X25hbWUiOiJBcnp0cHJheGlzIEFyenRwcmF4aXMiLCJwcnRfYWRtIjoiVHJ1ZSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJycGFhcnp0cHJheGlzQGdtYWlsLmNvbSIsIm5hbWUiOiJycGFhcnp0cHJheGlzQGdtYWlsLmNvbSIsImV4dF9pZHBfaWQiOiIxIiwiZXh0X2lkcF9kaXNwX25hbWUiOiJHbG9iYWxJZHAiLCJzaWQiOiJEQ0JBMDA1MkEzNjJEOTE3MUZERjdEQjNDMzZEQUZFQiIsImp0aSI6IjNEN0I3QkYyOTQyM0U0RkQ0NEEyRDc0MDBEMkQyQ0IxIn0.Zymk0JRBmNPLUsqj7s3S0QrzDUoOfxwT4hS1RZl2LZ1yrot8dNHnS0_UJpVjIHWIqmBSdvYvhNhAmCstGV2beBxkdAVUS9ZMMqX4BcBtY3E9XAhjyPHunW9jjKL5zDkMIpXJPvoDW9vtbQtz8n_mI0JK4KRXHOzfynuPRsAPSAW87GxJPqoJhaR0WTp2ijL0K6FbLe_l2xNP9p8XozigN_LpNdiIsZDt0nLmYs25enzTX22N22a0-vvwxubq7XAq_rSZ_OmeIcaVPPDN_VX1jiVHGA5SwoR3BSVbdhqYRN672ZVpI5gJqLr-ZbEvJIq8zNB99rqolY_6jwsybZlcZw";

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
            String ahvNumber = getParameterString(parameters, "ahvNummer");
            String cleaned_AHV = ahvNumber.replace("[", "").replace("]", "");
            String email = getParameterString(parameters, "email");
            String handynummer = getParameterString(parameters, "handynummer");
            // Save patient to MongoDB
            MongoClientConnection connection = new MongoClientConnection();
            GoogleCloudDialogflowV2IntentMessageText text = new GoogleCloudDialogflowV2IntentMessageText();
            if (connection.checkIfPatientExists(cleaned_AHV)) {
                text.setText(List.of("AHV-Nummer existiert bereits. Bitte geben Sie Ihre korrekte AHV Nummer an oder vereinbaren Sie über 'Termin' eine Besprechung."));
                msg.setText(text);
                connection.closeClient();}
            else {
                connection.savePatientToMongoDB(nachname, vorname, cleaned_AHV, email, handynummer);
                connection.closeClient();
                try {                
                    // Send GET request
                    String getResponse = sendGetRequest(getUrl, bearerToken);
                    System.out.println("GET Response: " + getResponse);
                    System.out.println("AHV Nummer bevor: " + ahvNumber.toString());
                    
                    System.out.println("AHV Nummer danach: " + cleaned_AHV);
    
                    // Send POST request
                    JSONObject postData = new JSONObject();
                    postData.put("ahvNumber", cleaned_AHV);
                    postData.put("email", email);
                    postData.put("handynummer", handynummer);
                    postData.put("nachname", nachname);
                    postData.put("vorname", vorname);
    
                    System.out.println("Postdata: " + postData.toString());
                    if(email != "default@example.com" &&  cleaned_AHV != "" && nachname != "" && vorname != "" && handynummer != "0"){
                        String postResponse = sendPostRequest(postUrl, bearerToken, postData.toString());
                        System.out.println("POST Response: " + postResponse);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
    
                ahvNumber="";
                text = new GoogleCloudDialogflowV2IntentMessageText();
                text.setText(List.of("Patient wurde erfolgreich registriert. Wenn Sie einen Termin benötigen schreiben Sie bitte 'Termin'?"));
                msg.setText(text);
                //Save Patient to UiPath
                System.out.println("Handle Patient Registration");
            }
            
        } else if ("TerminVereinbaren".equals(intent)) {
            MongoClientConnection connection = new MongoClientConnection();
           // Use your calendar ID
            Date date = new Date();
            int d = 7;
            ahvNumber = getParameterString(parameters, "AHVNumber");
            System.out.println("AHV: " + ahvNumber);
            

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
            }else if (!connection.checkIfPatientExists(ahvNumber)) {
                GoogleCloudDialogflowV2IntentMessageText text = new GoogleCloudDialogflowV2IntentMessageText();
                text.setText(List.of("AHV-Nummer nicht gefunden. Bitte registrieren Sie sich zuerst. Geben Sie hierfür 'Registrieren' ein."));
                msg.setText(text);
                connection.closeClient();
            } 
            else {

                List<String> freeSlots = googleCalendarService.findFreeTimeSlots(calendarId, date, d);
                msg.setText(new GoogleCloudDialogflowV2IntentMessageText().setText(List.of(
                        "Freie Termine:\n" + String.join(", ", freeSlots)
                                + "\n \nBitte wählen Sie einen Termin aus.")));
            }

        } else if ("TerminAuswählen".equals(intent)) {
            String termin = getParameterString(parameters, "dateTime");
            MongoClientConnection connection = new MongoClientConnection();
            String[] patientinfo = connection.getEmailAndNameByAhvnummer(ahvNumber);            
            String mail = patientinfo != null && patientinfo.length > 0 ? patientinfo[0] : "";
            String name = patientinfo != null && patientinfo.length > 1 ? patientinfo[1] : "";
            System.out.println("Mail: " + mail);
            System.out.println("Name: " + name);
            String terminString = termin.replace("[{date_time=", "").replace("}]", "");
            String responseMessage = googleCalendarService.validateAndCreateEvent(terminString, mail, name, calendarId);
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

    public String sendPostRequest(String url, String bearerToken, String jsonInputString) throws Exception {
        URL obj = new URI(url).toURL();
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        // Setting the request method to POST
        con.setRequestMethod("POST");

        // Adding headers
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Authorization", "Bearer " + bearerToken);

        // Enabling input and output streams
        con.setDoOutput(true);

        // Sending the JSON input string
        try (DataOutputStream wr = new DataOutputStream(con.getOutputStream())) {
            wr.writeBytes(jsonInputString);
            wr.flush();
        }

        // Handling the response
        int responseCode = con.getResponseCode();
        System.out.println("POST Response Code : " + responseCode);

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
