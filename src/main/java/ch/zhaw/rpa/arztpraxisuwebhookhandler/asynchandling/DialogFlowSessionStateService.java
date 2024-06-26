package ch.zhaw.rpa.arztpraxisuwebhookhandler.asynchandling;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DialogFlowSessionStateService {

    private List<DialogFlowSessionState> sessionStates = new ArrayList<>();
    

    public void addSessionState(DialogFlowSessionState sessionState) {
        sessionStates.add(sessionState);
    }

    public DialogFlowSessionState getSessionStateBySessionId(String sessionId) {
        return sessionStates.stream()
            .filter(sessionState -> sessionId.equals(sessionState.getDialogFlowSessionId()))
            .findFirst()
            .orElse(null);
    }

    public void removeSessionState(DialogFlowSessionState sessionState) {
        sessionStates.remove(sessionState);
    }
    

    // Alle 10 Minuten prüfen, ob es States gibt, welche älter als 1 Stunde sind und diese löschen
    @Scheduled(fixedRate = 600000)
    public void removeOldStates() {
        Instant now = Instant.now();
        List<DialogFlowSessionState> statesToRemove = new ArrayList<>();

        for (DialogFlowSessionState dialogFlowSessionState : sessionStates) {
            if (dialogFlowSessionState.getDialogFlowFirstRequestReceived().toInstant().isBefore(now.minus(1, ChronoUnit.HOURS))) {
                statesToRemove.add(dialogFlowSessionState);
            }
        }

        for (DialogFlowSessionState state : statesToRemove) {
            sessionStates.remove(state);
            System.out.println("Log: Auto-removed " + state.getDialogFlowSessionId());
        }
    }
}
