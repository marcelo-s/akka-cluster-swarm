package main;

import actors.Backend;
import actors.ClusterListener;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.management.javadsl.AkkaManagement;
import akka.routing.FromConfig;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import static utils.ConfigUtils.getConfig;

public class Backendmain {
    public static void main(String[] args) {

        final Config config = getConfig("backend", null);

        ActorSystem system = ActorSystem.create(Main.CLUSTER_SYSTEM_NAME, config);

        system.actorOf(Props.create(ClusterListener.class));

        String seedPort = ConfigFactory.load().getString("clustering.port");
        // Start management system only on port 2560
        if (seedPort.equals("2560")) {
            startManagementSystem(system);
        }

        // Use application.conf file to create backend router and deploy remotly
        system.actorOf(FromConfig.getInstance().props(Props.create(Backend.class)), "router1");
    }

    private static void startManagementSystem(ActorSystem system) {
        AkkaManagement.get(system).start();
    }
}