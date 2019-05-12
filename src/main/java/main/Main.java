package main;

import com.typesafe.config.ConfigFactory;

public class Main {

    public static String CLUSTER_SYSTEM_NAME = ConfigFactory.load().getString("clustering.cluster.name");

    public static void main(String[] args) {
        String role = ConfigFactory.load().getString("clustering.role");
        initNode(role);
    }

    private static void initNode(String role) {
        switch (role) {
            case "frontend":
                FrontendMain.main(new String[]{});
                break;
            case "backend":
                Backendmain.main(new String[]{});
                break;
            default:
                System.out.println("ERROR PARSING INPUT");
        }
    }
}
