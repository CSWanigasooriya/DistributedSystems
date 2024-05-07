package online.inventory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import online.inventory.grpc.generated.AddItemRequest;
import online.inventory.grpc.generated.AddItemServiceGrpc;
import online.inventory.grpc.generated.Item;
import online.inventory.grpc.generated.ItemManagerResponse;


import java.util.List;
import java.util.UUID;

public class AddItemService extends AddItemServiceGrpc.AddItemServiceImplBase implements DistributedTxListener {
    private final InventoryControlServer inventoryControlServer;

    private AddItemRequest addItemRequest;
    private int finalAddItemStatusCode;

    public AddItemService(InventoryControlServer inventoryControlServer) {
        this.inventoryControlServer = inventoryControlServer;
    }

    @Override
    public void addItem(AddItemRequest request, StreamObserver<ItemManagerResponse> responseObserver) {
        String itemCode = request.getItemCode();
        if (inventoryControlServer.getItemWithCode(itemCode) != null) {
            ItemManagerResponse response = ItemManagerResponse.newBuilder().setMessage("Item already exists.").setStatus(false).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            return;
        }

        try {
            startDistributedTx(request);
            updateSecondaryServers(request);

            if (inventoryControlServer.isLeader()) {
                System.out.println("Adding item as Primary");

                // Try to commit or abort based on business logic or validations
                if (isValidAddRequest(request)) {
                    ((DistributedTxCoordinator) inventoryControlServer.getTransaction()).perform();
                    finalAddItemStatusCode = 200; // success
                } else {
                    ((DistributedTxCoordinator) inventoryControlServer.getTransaction()).sendGlobalAbort();
                    finalAddItemStatusCode = 400; // bad request
                }
            } else {
                if (request.getIsSentByPrimary()) {
                    System.out.println("Adding item on Secondary, on Primary's command");
                    ((DistributedTxParticipant) inventoryControlServer.getTransaction()).voteCommit();
                } else {
                    ItemManagerResponse primaryResponse = callPrimary(request);
                    responseObserver.onNext(primaryResponse);
                    responseObserver.onCompleted();
                    return;
                }
            }

            ItemManagerResponse response = ItemManagerResponse.newBuilder().setStatus(finalAddItemStatusCode == 200).setMessage(updateAddItemMessage(finalAddItemStatusCode, request)).build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            System.out.println("Error while adding the item " + e.getMessage());
            e.printStackTrace();
            responseObserver.onNext(ItemManagerResponse.newBuilder().setStatus(false).setMessage("Error in adding item").build());
            responseObserver.onCompleted();
        }
    }

    private boolean isValidAddRequest(AddItemRequest request) {
        // Implement validation logic here
        return request.getQuantity() > 0;
    }

    private void updateSecondaryServers(AddItemRequest request) throws Exception {
        System.out.println("Updating other servers for new item");
        List<String[]> othersData = inventoryControlServer.getOthersData();
        for (String[] data : othersData) {
            callServer(request, data[0], Integer.parseInt(data[1]), true);
        }
    }

    private ItemManagerResponse callPrimary(AddItemRequest request) {
        System.out.println("Calling Primary server for add");
        String[] currentLeaderData = inventoryControlServer.getCurrentLeaderData();
        return callServer(request, currentLeaderData[0], Integer.parseInt(currentLeaderData[1]), false);
    }

    private ItemManagerResponse callServer(AddItemRequest request, String ipAddress, int port, boolean isSentByPrimary) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(ipAddress, port).usePlaintext().build();
        AddItemServiceGrpc.AddItemServiceBlockingStub clientStub = AddItemServiceGrpc.newBlockingStub(channel);
        AddItemRequest updatedRequest = AddItemRequest.newBuilder(request).setIsSentByPrimary(isSentByPrimary).build();
        return clientStub.addItem(updatedRequest);
    }

    private void startDistributedTx(AddItemRequest request) {
        try {
            DistributedTx transaction = inventoryControlServer.getTransaction();
            transaction.start(request.getItemCode(), String.valueOf(UUID.randomUUID()));
            this.addItemRequest = request;
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onGlobalCommit() {
        if (addItemRequest != null) {
            inventoryControlServer.setItemWithCode(addItemRequest.getItemCode(), Item.newBuilder().setItemCode(addItemRequest.getItemCode()).setItemName(addItemRequest.getItemName()).setQuantity(addItemRequest.getQuantity()).setUnitPrice(addItemRequest.getUnitPrice()).build());
            addItemRequest = null;
            finalAddItemStatusCode = 200;
        }
    }

    @Override
    public void onGlobalAbort() {
        addItemRequest = null;
        System.out.println("Transaction Aborted by the Coordinator");
    }

    private String updateAddItemMessage(int code, AddItemRequest request) {
        switch (code) {
            case 200:
                return String.format("Success!!!... Item %s added with quantity %d", request.getItemCode(), request.getQuantity());
            case 400:
                return "Invalid input!!!... Check the item details!";
            default:
                return "Unexpected error occurred!";
        }
    }
}