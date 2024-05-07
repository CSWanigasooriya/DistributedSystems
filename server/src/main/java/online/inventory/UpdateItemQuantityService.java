package online.inventory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.zookeeper.KeeperException;
import org.online.server.grpc.generated.Item;
import org.online.server.grpc.generated.ItemManagerResponse;
import org.online.server.grpc.generated.UpdateItemQuantityRequest;
import org.online.server.grpc.generated.UpdateItemQuantityServiceGrpc;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class UpdateItemQuantityService extends UpdateItemQuantityServiceGrpc.UpdateItemQuantityServiceImplBase implements DistributedTxListener {
    UpdateItemQuantityServiceGrpc.UpdateItemQuantityServiceBlockingStub clientStub = null;
    private final InventoryControlServer inventoryControlServer;

    private UpdateItemQuantityRequest updateItemQuantityRequest;

    private int finalUpdateItemQuantityStatusCode;

    public UpdateItemQuantityService(InventoryControlServer inventoryControlServer) {
        this.inventoryControlServer = inventoryControlServer;
    }

    @Override
    public void updateItemQuantity(UpdateItemQuantityRequest updateItemQuantityRequest, StreamObserver<ItemManagerResponse> itemManagerResponse) {
        String itemCode = updateItemQuantityRequest.getItemCode();
        int quantity = updateItemQuantityRequest.getQuantity();

        boolean transactionStatus = false;
        if (inventoryControlServer.isLeader()) { // Act as primary
            try {
                System.out.println("Updating Item as Primary");

                startDistributedTx(updateItemQuantityRequest);
                updateSecondaryServers(itemCode, quantity);

                System.out.println("going to perform");

                // Perform or abort the distributed transaction based on the quantity
                if (quantity > 0) {
                    ((DistributedTxCoordinator) inventoryControlServer.getTransaction()).perform();
                } else {
                    ((DistributedTxCoordinator) inventoryControlServer.getTransaction()).sendGlobalAbort();
                }

                transactionStatus = true;
            } catch (Exception e) {
                System.out.println("Error while updating the item " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            if (updateItemQuantityRequest.getIsSentByPrimary()) {
                System.out.println("Updating item balance on secondary, on Primary's command");

                startDistributedTx(updateItemQuantityRequest);

                // Vote for commit or abort the distributed transaction based on the quantity
                if (quantity != 0.0d) {
                    ((DistributedTxParticipant) inventoryControlServer.getTransaction()).voteCommit();
                } else {
                    ((DistributedTxParticipant) inventoryControlServer.getTransaction()).voteAbort();
                }

                transactionStatus = true;
            } else {
                // Call the primary server to handle the update
                ItemManagerResponse response = callPrimary(itemCode, quantity);

                if (response.getStatus()) {
                    transactionStatus = true;
                }
            }
        }

        // Create and send the response to the client
        ItemManagerResponse response = ItemManagerResponse.newBuilder().setStatus(transactionStatus).setMessage(updateQuantityMessageHandler(finalUpdateItemQuantityStatusCode, updateItemQuantityRequest)).build();
        itemManagerResponse.onNext(response);
        itemManagerResponse.onCompleted();
    }

    private void updateItemQuantity() {
        if (updateItemQuantityRequest != null) {
            String itemCode = updateItemQuantityRequest.getItemCode();
            int quantity = updateItemQuantityRequest.getQuantity();

            // Get the item from the server
            Item item = inventoryControlServer.getItemWithCode(itemCode);

            if (item != null) {
                // Modify the item with the new quantity and update it
                Item modifiedItem = item.toBuilder().setQuantity(quantity).build();
                inventoryControlServer.setItemWithCode(modifiedItem.getItemCode(), modifiedItem);
                finalUpdateItemQuantityStatusCode = 200;
            } else {
                finalUpdateItemQuantityStatusCode = 404;
            }

            updateQuantityMessagePrinter(finalUpdateItemQuantityStatusCode, updateItemQuantityRequest);

            updateItemQuantityRequest = null;
        }
    }

    private void updateQuantityMessagePrinter(int code, UpdateItemQuantityRequest updateItemQuantityRequest) {
        System.out.println(updateQuantityMessageHandler(code, updateItemQuantityRequest));
    }

    private String updateQuantityMessageHandler(int code, UpdateItemQuantityRequest updateItemQuantityRequest) {
        if (code == 200) {
            return String.format("Success!!!... Item %s updated the quantity with %d", updateItemQuantityRequest.getItemCode(), updateItemQuantityRequest.getQuantity());
        } else if (code == 404) {
            return "Invalid input!!!... No such item to update!";
        } else {
            return "Unexpected error occurred!";
        }
    }

    private ItemManagerResponse callServer(String itemCode, int quantity, boolean isSentByPrimary, String ipAddress, int port) {
        System.out.println("Call Server " + ipAddress + ":" + port);
        ManagedChannel channel = ManagedChannelBuilder.forAddress(ipAddress, port).usePlaintext().build();
        clientStub = UpdateItemQuantityServiceGrpc.newBlockingStub(channel);

        UpdateItemQuantityRequest request = UpdateItemQuantityRequest.newBuilder().setItemCode(itemCode).setQuantity(quantity).setIsSentByPrimary(isSentByPrimary).build();
        return clientStub.updateItemQuantity(request);
    }

    private ItemManagerResponse callPrimary(String itemCode, int quantity) {
        System.out.println("Calling Primary server");
        String[] currentLeaderData = inventoryControlServer.getCurrentLeaderData();
        String IPAddress = currentLeaderData[0];
        int port = Integer.parseInt(currentLeaderData[1]);
        return callServer(itemCode, quantity, false, IPAddress, port);
    }

    private void updateSecondaryServers(String itemCode, int quantity) throws KeeperException, InterruptedException {
        System.out.println("Updating other servers");
        List<String[]> othersData = inventoryControlServer.getOthersData();
        for (String[] data : othersData) {
            String IPAddress = data[0];
            int port = Integer.parseInt(data[1]);
            callServer(itemCode, quantity, true, IPAddress, port);
        }
    }

    private void startDistributedTx(UpdateItemQuantityRequest updateItemQuantityRequest) {
        try {
            DistributedTx transaction = inventoryControlServer.getTransaction();

            // Start the distributed transaction with a unique ID
            transaction.start(updateItemQuantityRequest.getItemCode(), String.valueOf(UUID.randomUUID()));

            this.updateItemQuantityRequest = updateItemQuantityRequest;
        } catch (IOException ignored) {
        }
    }


    @Override
    public void onGlobalCommit() {
        updateItemQuantity();
    }

    @Override
    public void onGlobalAbort() {
        updateItemQuantityRequest = null;
        System.out.println("Transaction Aborted by the Coordinator");
    }
}
