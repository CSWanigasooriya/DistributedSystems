package online.inventory;

import java.util.HashMap;
import java.util.Map;

public class CredentialStore {
    private final Map<String, String> credentials = new HashMap<>();

    public CredentialStore() {
        credentials.put("seller", "123");
    }

    public boolean validateCredentials(String username, String password) {
        return credentials.containsKey(username) && credentials.get(username).equals(password);
    }
}