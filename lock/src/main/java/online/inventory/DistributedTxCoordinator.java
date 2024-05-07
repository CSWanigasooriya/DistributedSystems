package online.inventory;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class DistributedTxCoordinator extends DistributedTx {
    private static final Logger logger = LoggerFactory.getLogger(DistributedTxCoordinator.class);

    public DistributedTxCoordinator(List<DistributedTxListener> listener) {
        super(listener);
    }

    void onStartTransaction(String transactionId, String participantId) {
        try {
            currentTransaction = "/" + transactionId;
            client.createNode(currentTransaction, CreateMode.PERSISTENT, "".getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error("An error occurred", e);
        }
    }

    public void perform() throws KeeperException, InterruptedException {
        List<String> childrenNodePaths = client.getChildrenNodePaths(currentTransaction);
        byte[] data;
        System.out.println("Child count : " + childrenNodePaths.size());
        for (String path : childrenNodePaths) {
            path = currentTransaction + "/" + path;
            System.out.println("Checking path :" + path);
            data = client.getData(path, false);
            String dataString = new String(data);
            if (!VOTE_COMMIT.equals(dataString)) {
                System.out.println("Child " + path + " caused the transaction to abort. Sending GLOBAL_ABORT");
                sendGlobalAbort();
            }
        }
        System.out.println("All nodes are okay to commit the transaction. Sending GLOBAL_COMMIT");
        sendGlobalCommit();
        reset();
    }

    public void sendGlobalCommit() throws KeeperException, InterruptedException {
        if (currentTransaction != null) {
            System.out.println("Sending global commit for " + currentTransaction);
            client.write(currentTransaction, DistributedTxCoordinator.GLOBAL_COMMIT.getBytes(StandardCharsets.UTF_8));
            listener.forEach(DistributedTxListener::onGlobalCommit);
        }
    }

    public void sendGlobalAbort() throws KeeperException, InterruptedException {
        if (currentTransaction != null) {
            System.out.println("Sending global abort for " + currentTransaction);
            client.write(currentTransaction, DistributedTxCoordinator.GLOBAL_ABORT.getBytes(StandardCharsets.UTF_8));
            listener.forEach(DistributedTxListener::onGlobalAbort);
        }
    }

    private void reset() throws KeeperException, InterruptedException {
        client.forceDelete(currentTransaction);
        currentTransaction = null;
    }
}
