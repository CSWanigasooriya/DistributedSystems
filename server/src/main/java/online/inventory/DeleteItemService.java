package online.inventory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import online.inventory.grpc.generated.DeleteItemRequest;
import online.inventory.grpc.generated.DeleteItemServiceGrpc;
import online.inventory.grpc.generated.Item;
import online.inventory.grpc.generated.ItemManagerResponse;


import java.util.List;
import java.util.UUID;

public class DeleteItemService extends DeleteItemServiceGrpc.DeleteItemServiceImplBase implements DistributedTxListener {
    private final InventoryControlServer inventoryControlServer;
    private DeleteItemRequest deleteItemRequest;

    public DeleteItemService(InventoryControlServer inventoryControlServer) {
        this.inventoryControlServer = inventoryControlServer;
    }

    @Override
    public void deleteItem(DeleteItemRequest request, StreamObserver<ItemManagerResponse> responseObserver) {
        String itemCode = request.getItemCode();
        Item item = inventoryControlServer.getItemWithCode(itemCode);

        if (item != null) {
            try {
                startDistributedTx(request);
                updateSecondaryServers(itemCode);

                if (inventoryControlServer.isLeader()) {
                    System.out.println("Attempting to delete item as Primary");
                    ((DistributedTxCoordinator) inventoryControlServer.getTransaction()).perform();
                } else {
                    if (request.getIsSentByPrimary()) {
                        System.out.println("Deleting item on Secondary, on Primary's command");
                        ((DistributedTxParticipant) inventoryControlServer.getTransaction()).voteCommit();
                    } else {
                        ItemManagerResponse primaryResponse = callPrimary(itemCode);
                        responseObserver.onNext(primaryResponse);
                        responseObserver.onCompleted();
                        return;
                    }
                }

                ItemManagerResponse response = ItemManagerResponse.newBuilder()
                        .setItemName(item.getItemName())
                        .setMessage("Item successfully deleted.")
                        .setStatus(true)
                        .build();
                responseObserver.onNext(response);
            } catch (Exception e) {
                System.out.println("Error while deleting item: " + e.getMessage());
                e.printStackTrace();
                responseObserver.onNext(ItemManagerResponse.newBuilder()
                        .setMessage("Error in deleting item")
                        .setStatus(false)
                        .build());
            }
        } else {
            responseObserver.onNext(ItemManagerResponse.newBuilder()
                    .setMessage("Item not found.")
                    .setStatus(false)
                    .build());
        }
        responseObserver.onCompleted();
    }

    private void updateSecondaryServers(String itemCode) throws Exception {
        System.out.println("Updating other servers for item deletion");
        List<String[]> othersData = inventoryControlServer.getOthersData();
        for (String[] data : othersData) {
            callServer(itemCode, data[0], Integer.parseInt(data[1]), true);
        }
    }

    private ItemManagerResponse callPrimary(String itemCode) {
        System.out.println("Calling Primary server for delete");
        String[] currentLeaderData = inventoryControlServer.getCurrentLeaderData();
        return callServer(itemCode, currentLeaderData[0], Integer.parseInt(currentLeaderData[1]), false);
    }

    private ItemManagerResponse callServer(String itemCode, String ipAddress, int port, boolean isSentByPrimary) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(ipAddress, port).usePlaintext().build();
        DeleteItemServiceGrpc.DeleteItemServiceBlockingStub clientStub = DeleteItemServiceGrpc.newBlockingStub(channel);
        DeleteItemRequest request = DeleteItemRequest.newBuilder()
                .setItemCode(itemCode)
                .setIsSentByPrimary(isSentByPrimary)
                .build();
        return clientStub.deleteItem(request);
    }

    private void startDistributedTx(DeleteItemRequest request) {
        try {
            DistributedTx transaction = inventoryControlServer.getTransaction();
            transaction.start(request.getItemCode(), String.valueOf(UUID.randomUUID()));
            this.deleteItemRequest = request;
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onGlobalCommit() {
        if (deleteItemRequest != null) {
            inventoryControlServer.removeItem(deleteItemRequest.getItemCode());
            deleteItemRequest = null;
        }
    }

    @Override
    public void onGlobalAbort() {
        System.out.println("Transaction Aborted by the Coordinator");
        deleteItemRequest = null;
    }
}