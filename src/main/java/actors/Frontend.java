package actors;

import akka.actor.AbstractLoggingActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import com.typesafe.config.ConfigFactory;

import static main.Main.CLUSTER_SYSTEM_NAME;
import static messages.AppMessages.FailedMessage;
import static messages.AppMessages.JobMessage;

public class Frontend extends AbstractLoggingActor {

    private boolean hasBackend;
    private Cluster cluster = Cluster.get(getContext().system());

    // Subscribe to cluster events
    @Override
    public void preStart() {
        cluster.subscribe(self(), ClusterEvent.MemberUp.class);
    }

    // Unsubscribe when finished
    @Override
    public void postStop() {
        cluster.unsubscribe(self());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(JobMessage.class, job -> !hasBackend, this::onJobReceivedNoBackend)
                .match(JobMessage.class, this::onJobReceived)
                .match(ClusterEvent.MemberUp.class, this::onMemberUp)
                .build();
    }

    private void onJobReceivedNoBackend(JobMessage job) {
        sender().tell(new FailedMessage("Service unavailable, try again later", job), sender());
    }

    private void onJobReceived(JobMessage job) {
        String workerIP = ConfigFactory.load().getString("clustering.seed1.ip");
        String seedPort = ConfigFactory.load().getString("clustering.seed1.port");
        String address = String.format("akka.tcp://%s@" + workerIP + ":" + seedPort, CLUSTER_SYSTEM_NAME);
        // Forwards to specific router on seed1.
        // Seed1 does not have local routees, but it has remote deployed routees on seed2, so seed2 is the one processing the jobs.
        // If allow-local-routees is set to "on" in the application.conf file, both local and remote routees process the jobs.
        getContext().actorSelection(address + "/user/router1").forward(job, getContext());
    }

    private void onMemberUp(ClusterEvent.MemberUp memberUp) {
        Member member = memberUp.member();
        if (member.hasRole("backend")) {
            hasBackend = true;
        }
    }
}
