package ch.zhaw.rpa.arztpraxisuwebhookhandler.handler;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.regex.Matcher;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2IntentMessage;
import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2IntentMessageText;
import com.google.api.services.dialogflow.v2.model.GoogleCloudDialogflowV2WebhookRequest;

import ch.zhaw.rpa.arztpraxisuwebhookhandler.asynchandling.DialogFlowSessionState;
import ch.zhaw.rpa.arztpraxisuwebhookhandler.asynchandling.DialogFlowSessionStateService;
import ch.zhaw.rpa.arztpraxisuwebhookhandler.asynchandling.UiPathAsyncJobHandler;

@Component
public class UiPathHandler {

    @Autowired
    private UiPathAsyncJobHandler uiPathAsyncJobHandler;

    @Autowired
    private DialogFlowSessionStateService stateService;

    public GoogleCloudDialogflowV2IntentMessage handlePatientRegistration(GoogleCloudDialogflowV2WebhookRequest request,
                    String vorname, String nachname, String ahvNumber, String email, String handynummer, GoogleCloudDialogflowV2IntentMessage msg) {

        // Session Id auslesen
        String sessionId = request.getSession();

        // Prüfen, ob Session Id bereits verwaltet ist
        DialogFlowSessionState sessionState = stateService.getSessionStateBySessionId(sessionId);
        
        System.out.println("Check AHV");
        if(! checkAhvNBumber(ahvNumber)){
            GoogleCloudDialogflowV2IntentMessageText text = new GoogleCloudDialogflowV2IntentMessageText();
            text.setText(List.of("Es wurde eine ungültige AHV-Nummer bei der Registrierung eingegeben. Bitte gib eine korrekte ein. "));
            msg.setText(text);
            stateService.removeSessionState(sessionState);
            return msg;
        }

        // Wenn die Session Id noch nicht verwaltet ist (erster Request)
        if (sessionState == null) {
            // Neuen Session State erstellen
            sessionState = DialogFlowSessionState.builder().DialogFlowSessionId(sessionId)
                            .DialogFlowFirstRequestReceived(new Date()).uiPathExceptionMessage("").build();

            stateService.addSessionState(sessionState);

            // Async den Auftrag für den UiPath-Job erteilen
            uiPathAsyncJobHandler.asyncRunUiPathCreatePatientConnector(sessionState, vorname, nachname, ahvNumber, email, handynummer);
            System.out.println("!!!!!!!!! AsyncHandler aufgerufen für Session Id " + sessionId);

            // Antwort an den Benutzer senden
            msg = createWaitingMessage(msg, "Es kann einen Moment dauern, bis Ihre Daten verarbeitet sind. Bitte sagen Sie 'Weiter', um zu prüfen, ob die Registrierung abgeschlossen ist.");
        }
        // Wenn ein zweiter, dritter, usw. Request vorhanden ist
        else {
            // Wenn der UiPath Job noch am laufen ist
            if (sessionState.getUiPathJobState().equals("created")) {
                // Antwort an den Benutzer senden
                msg = createWaitingMessage(msg, "Ihre Registrierung wird noch bearbeitet. Bitte sagen Sie 'Weiter', um erneut zu prüfen.");
            }
            // Wenn der UiPath Job abgeschlossen wurde
            else if (sessionState.getUiPathJobState().equals("successfull")) {
                GoogleCloudDialogflowV2IntentMessageText text = new GoogleCloudDialogflowV2IntentMessageText();
                text.setText(List.of("Der Patient wurde erfolgreich registriert."));
                msg.setText(text);

                stateService.removeSessionState(sessionState);
            }
            // In allen anderen Fällen (UiPath Job nicht erstellt werden konnte oder fehlgeschlagen)
            else {
                GoogleCloudDialogflowV2IntentMessageText text = new GoogleCloudDialogflowV2IntentMessageText();
                text.setText(List.of((sessionState.getUiPathExceptionMessage().isEmpty()
                                ? "Ein unerwarteter Fehler ist aufgetreten."
                                : "Folgender Fehler ist aufgetreten: "
                                                + sessionState.getUiPathExceptionMessage())));
                msg.setText(text);
                stateService.removeSessionState(sessionState);
            }
        }

        return msg;
    }

    private boolean checkAhvNBumber(String ahv){
        // Define the regex pattern for the structure 123.4567.89.12
        String regex = "\\d{3}\\.\\d{4}\\.\\d{4}\\.\\d{2}";
        
        // Compile the pattern
        Pattern pattern = Pattern.compile(regex);
        
        // Match the input string against the pattern
        Matcher matcher = pattern.matcher(ahv);
        
        // Return whether the input string matches the pattern
        return matcher.matches();
    }

    public GoogleCloudDialogflowV2IntentMessage handleAppointmentRequest(GoogleCloudDialogflowV2WebhookRequest request,
                    String ahvNumber, GoogleCloudDialogflowV2IntentMessage msg) {

        // Session Id auslesen
        String sessionId = request.getSession();

        // Prüfen, ob Session Id bereits verwaltet ist
        DialogFlowSessionState sessionState = stateService.getSessionStateBySessionId(sessionId);

        // Wenn die Session Id noch nicht verwaltet ist (erster Request)
        if (sessionState == null) {
            // Neuen Session State erstellen
            sessionState = DialogFlowSessionState.builder().DialogFlowSessionId(sessionId)
                            .DialogFlowFirstRequestReceived(new Date()).uiPathExceptionMessage("").build();

            stateService.addSessionState(sessionState);

            // Async den Auftrag für den UiPath-Job erteilen
            uiPathAsyncJobHandler.asyncRunUiPathGetAvailableAppointments(sessionState, ahvNumber);
            System.out.println("!!!!!!!!! AsyncHandler aufgerufen für Session Id " + sessionId);

            // Antwort an den Benutzer senden
            msg = createWaitingMessage(msg, "Es kann einen Moment dauern, bis die verfügbaren Termine geladen sind. Bitte sagen Sie 'Weiter', um zu prüfen, ob die Termine geladen sind.");
        } else {
            // Wenn der UiPath Job noch am laufen ist
            if (sessionState.getUiPathJobState().equals("created")) {
                // Antwort an den Benutzer senden
                msg = createWaitingMessage(msg, "Die Termine werden noch geladen. Bitte sagen Sie 'Weiter', um erneut zu prüfen.");
            }
            // Wenn der UiPath Job abgeschlossen wurde
            else if (sessionState.getUiPathJobState().equals("successfull")) {
                List<String> freieTermine = sessionState.getOutputArguments().getJSONArray("availableAppointments").toList()
                                .stream().map(Object::toString).collect(Collectors.toList());
                GoogleCloudDialogflowV2IntentMessageText text = new GoogleCloudDialogflowV2IntentMessageText();
                text.setText(List.of("Die folgenden Termine sind verfügbar: " + String.join(", ", freieTermine)));
                msg.setText(text);
            } else {
                GoogleCloudDialogflowV2IntentMessageText text = new GoogleCloudDialogflowV2IntentMessageText();
                text.setText(List.of((sessionState.getUiPathExceptionMessage().isEmpty()
                                ? "Ein unerwarteter Fehler ist aufgetreten."
                                : "Folgender Fehler ist aufgetreten: "
                                                + sessionState.getUiPathExceptionMessage())));
                msg.setText(text);
                stateService.removeSessionState(sessionState);
            }
        }

        return msg;
    }

    public GoogleCloudDialogflowV2IntentMessage handleAppointmentSelection(GoogleCloudDialogflowV2WebhookRequest request,
                    String termin, GoogleCloudDialogflowV2IntentMessage msg) {

        // Session Id auslesen
        String sessionId = request.getSession();

        // Prüfen, ob Session Id bereits verwaltet ist
        DialogFlowSessionState sessionState = stateService.getSessionStateBySessionId(sessionId);

        // Wenn die Session Id noch nicht verwaltet ist (erster Request)
        if (sessionState == null) {
            // Neuen Session State erstellen
            sessionState = DialogFlowSessionState.builder().DialogFlowSessionId(sessionId)
                            .DialogFlowFirstRequestReceived(new Date()).uiPathExceptionMessage("").build();

            stateService.addSessionState(sessionState);
        }

        // Async den Auftrag für den UiPath-Job erteilen
        uiPathAsyncJobHandler.asyncRunUiPathBookAppointment(sessionState, termin);
        System.out.println("!!!!!!!!! AsyncHandler aufgerufen für Session Id " + sessionId);

        // Antwort an den Benutzer senden
        msg = createWaitingMessage(msg, "Es kann einen Moment dauern, bis Ihr Termin gebucht ist. Bitte sagen Sie 'Weiter', um zu prüfen, ob die Buchung abgeschlossen ist.");

        return msg;
    }

    public GoogleCloudDialogflowV2IntentMessage handleContinueRequestForPatient(GoogleCloudDialogflowV2WebhookRequest request, GoogleCloudDialogflowV2IntentMessage msg) {
        String sessionId = request.getSession();
        DialogFlowSessionState sessionState = stateService.getSessionStateBySessionId(sessionId);

        if (sessionState == null) {
            return createErrorMessage(msg, "Es gibt keine laufende Anfrage für die Patientenregistrierung. Bitte starten Sie die Anfrage erneut.");
        }

        // Prüfen des Job-Status und entsprechend reagieren
        if (sessionState.getUiPathJobState().equals("created") || sessionState.getUiPathJobState().equals("running")) {
            msg = createWaitingMessage(msg, "Ihre Registrierung wird noch bearbeitet. Bitte sagen Sie 'Weiter', um erneut zu prüfen.");
        } else if (sessionState.getUiPathJobState().equals("successfull")) {
            GoogleCloudDialogflowV2IntentMessageText text = new GoogleCloudDialogflowV2IntentMessageText();
            text.setText(List.of("Die Patientenregistrierung war erfolgreich."));
            msg.setText(text);
            stateService.removeSessionState(sessionState);
        } else {
            msg = createErrorMessage(msg, sessionState.getUiPathExceptionMessage());
            stateService.removeSessionState(sessionState);
        }

        return msg;
    }

    public GoogleCloudDialogflowV2IntentMessage handleContinueRequestForAppointment(GoogleCloudDialogflowV2WebhookRequest request, GoogleCloudDialogflowV2IntentMessage msg) {
        String sessionId = request.getSession();
        DialogFlowSessionState sessionState = stateService.getSessionStateBySessionId(sessionId);

        if (sessionState == null) {
            return createErrorMessage(msg, "Es gibt keine laufende Anfrage für die Terminvereinbarung. Bitte starten Sie die Anfrage erneut.");
        }

        // Prüfen des Job-Status und entsprechend reagieren
        if (sessionState.getUiPathJobState().equals("created") || sessionState.getUiPathJobState().equals("running")) {
            msg = createWaitingMessage(msg, "Ihre Terminanfrage wird noch bearbeitet. Bitte sagen Sie 'Weiter', um erneut zu prüfen.");
        } else if (sessionState.getUiPathJobState().equals("successfull")) {
            GoogleCloudDialogflowV2IntentMessageText text = new GoogleCloudDialogflowV2IntentMessageText();
            text.setText(List.of("Die Terminvereinbarung war erfolgreich."));
            msg.setText(text);
            stateService.removeSessionState(sessionState);
        } else {
            msg = createErrorMessage(msg, sessionState.getUiPathExceptionMessage());
            stateService.removeSessionState(sessionState);
        }

        return msg;
    }

    private GoogleCloudDialogflowV2IntentMessage createErrorMessage(GoogleCloudDialogflowV2IntentMessage msg, String errorMessage) {
        GoogleCloudDialogflowV2IntentMessageText text = new GoogleCloudDialogflowV2IntentMessageText();
        text.setText(List.of(errorMessage));
        msg.setText(text);
        return msg;
    }

    private GoogleCloudDialogflowV2IntentMessage createWaitingMessage(GoogleCloudDialogflowV2IntentMessage msg, String waitingMessage) {
        GoogleCloudDialogflowV2IntentMessageText text = new GoogleCloudDialogflowV2IntentMessageText();
        text.setText(List.of(waitingMessage));
        msg.setText(text);
        return msg;
    }
}
