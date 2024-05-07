package online.inventory;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class DistributedLock implements Watcher {
    public static String zooKeeperUrl;
    private final ZooKeeperClient client;
    private final byte[] dataBytes;
    CountDownLatch startFlag = new CountDownLatch(1);
    CountDownLatch eventReceivedFlag;
    private String childPath;
    private String lockPath;
    private boolean isAcquired = false;
    private String watchedNode;


    public DistributedLock(String lockName, String data) throws IOException, KeeperException, InterruptedException {
        dataBytes = data.getBytes(StandardCharsets.UTF_8);
        this.lockPath = "/" + lockName;
        client = new ZooKeeperClient(zooKeeperUrl, 5000, this);
        startFlag.await();
        if (!client.CheckExists(lockPath)) {
            createRootNode();
        }
        createChildNode();
    }

    public static void setZooKeeperURL(String url) {
        zooKeeperUrl = url;
    }

    private void createRootNode() throws InterruptedException, KeeperException {
        lockPath = client.createNode(lockPath, CreateMode.PERSISTENT, "".getBytes(StandardCharsets.UTF_8));
        System.out.println("Root node created at " + lockPath);
    }

    private void createChildNode() throws InterruptedException, UnsupportedEncodingException, KeeperException {
        String LOCK_PROCESS_PATH = "/lp_";
        childPath = client.createNode(lockPath + LOCK_PROCESS_PATH, CreateMode.EPHEMERAL_SEQUENTIAL, dataBytes);
        System.out.println("Child node created at " + childPath);
    }

    public void acquireLock() throws KeeperException, InterruptedException, UnsupportedEncodingException {
        String smallestNode = findSmallestNodePath();
        if (!smallestNode.equals(childPath)) {
            do {
                System.out.println("Lock is currently acquired by node " + smallestNode + " .. hence waiting..");
                eventReceivedFlag = new CountDownLatch(1);
                watchedNode = smallestNode;
                client.addWatch(smallestNode);
                eventReceivedFlag.await();
                smallestNode = findSmallestNodePath();
            } while (!smallestNode.equals(childPath));
        }
        isAcquired = true;
    }

    public void releaseLock() throws KeeperException, InterruptedException {
        if (!isAcquired) {
            throw new IllegalStateException("Lock needs to be acquired first to release");
        }
        client.delete(childPath);
        isAcquired = false;
    }

    public boolean tryAcquireLock() throws KeeperException, InterruptedException {
        String smallestNode = findSmallestNodePath();
        if (smallestNode.equals(childPath)) {
            isAcquired = true;
        }
        return isAcquired;
    }

    public byte[] getLockHolderData() throws KeeperException, InterruptedException {
        String smallestNode = findSmallestNodePath();
        return client.getData(smallestNode, true);
    }

    public List<byte[]> getOthersData() throws KeeperException, InterruptedException {
        List<byte[]> result = new ArrayList<>();
        List<String> childrenNodePaths = client.getChildrenNodePaths(lockPath);
        for (String path : childrenNodePaths) {
            path = lockPath + "/" + path;
            if (!path.equals(childPath)) {
                System.out.println("path :" + path + ", childPath" + childPath);
                System.out.println("Fetching data of node :" + path);
                byte[] data = client.getData(path, false);
                result.add(data);
            }
        }
        return result;
    }

    private String findSmallestNodePath() throws KeeperException, InterruptedException {
        List<String> childrenNodePaths = null;
        childrenNodePaths = client.getChildrenNodePaths(lockPath);
        Collections.sort(childrenNodePaths);
        String smallestPath = childrenNodePaths.get(0);
        smallestPath = lockPath + "/" + smallestPath;
        return smallestPath;
    }

    @Override
    public void process(WatchedEvent event) {
        Event.KeeperState state = event.getState();
        Event.EventType type = event.getType();
        if (Event.KeeperState.SyncConnected == state) {
            if (Event.EventType.None == type) {
                // Identify successful connection
                System.out.println("Successful connected to the server");
                startFlag.countDown();
            }
        }
        if (Event.EventType.NodeDeleted.equals(type)) {
            if (watchedNode != null && eventReceivedFlag != null && event.getPath().equals(watchedNode)) {
                System.out.println("NodeDelete event received. Trying to get the lock..");
                eventReceivedFlag.countDown();
            }
        }
    }
}
