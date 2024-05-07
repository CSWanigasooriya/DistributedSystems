package online.inventory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import online.inventory.grpc.generated.Item;
import online.inventory.grpc.generated.PlaceOrderRequest;
import online.inventory.grpc.generated.PlaceOrderResponse;
import online.inventory.grpc.generated.PlaceOrderServiceGrpc;
import org.apache.zookeeper.KeeperException;


import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class PlaceOrderService extends PlaceOrderServiceGrpc.PlaceOrderServiceImplBase implements DistributedTxListener {
    PlaceOrderServiceGrpc.PlaceOrderServiceBlockingStub clientStub = null;
    private final InventoryControlServer inventoryControlServer;

    private PlaceOrderRequest placeOrderRequest;

    private int finalPlaceOrderStatusCode;
    private double finalTotal;

    public PlaceOrderService(InventoryControlServer inventoryControlServer) {
        this.inventoryControlServer = inventoryControlServer;
    }

    @Override
    public void placeOrder(PlaceOrderRequest placeOrderRequest, StreamObserver<PlaceOrderResponse> placeOrderResponse) {
        String itemCode = placeOrderRequest.getItemCode();
        int quantity = placeOrderRequest.getQuantity();

        boolean transactionStatus = false;
        if (inventoryControlServer.isLeader()) { // Act as primary
            try {
                startDistributedTx(placeOrderRequest);
                updateSecondaryServers(itemCode, quantity);

                System.out.println("going to perform");

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
        } else { // Act As Secondary
            if (placeOrderRequest.getIsSentByPrimary()) {
                System.out.println("Updating item balance on secondary, on Primary's command");

                startDistributedTx(placeOrderRequest);

                if (quantity != 0.0d) {
                    ((DistributedTxParticipant) inventoryControlServer.getTransaction()).voteCommit();
                } else {
                    ((DistributedTxParticipant) inventoryControlServer.getTransaction()).voteAbort();
                }
                transactionStatus = true;
            } else {
                PlaceOrderResponse response = callPrimary(itemCode, quantity);

                if (response.getStatus()) {
                    transactionStatus = true;
                }
            }
        }

        PlaceOrderResponse response = PlaceOrderResponse.newBuilder().setStatus(transactionStatus).setMessage(placeOrderMessageHandler(finalPlaceOrderStatusCode, placeOrderRequest)).build();
        placeOrderResponse.onNext(response);
        placeOrderResponse.onCompleted();
    }

    private void placeOrder() {
        if (placeOrderRequest != null) {
            String itemCode = placeOrderRequest.getItemCode();
            Item item = inventoryControlServer.getItemWithCode(itemCode);

            if (item != null) {
                int quantity = item.getQuantity() - placeOrderRequest.getQuantity();

                if (quantity >= 0) {
                    Item modifiedItem = item.toBuilder().setQuantity(quantity).build();
                    inventoryControlServer.removeItem(placeOrderRequest.getItemCode());
                    inventoryControlServer.setItemWithCode(modifiedItem.getItemCode(), modifiedItem);
                    finalPlaceOrderStatusCode = 200;
                    finalTotal = item.getUnitPrice() * placeOrderRequest.getQuantity();
                } else {
                    finalPlaceOrderStatusCode = 401;
                }
            } else {
                finalPlaceOrderStatusCode = 404;
            }
            placeOderMessagePrinter(finalPlaceOrderStatusCode, placeOrderRequest);

            placeOrderRequest = null;
        }
    }

    private void placeOderMessagePrinter(int code, PlaceOrderRequest placeOrderRequest) {
        System.out.println(placeOrderMessageHandler(code, placeOrderRequest));
    }

    private String placeOrderMessageHandler(int code, PlaceOrderRequest placeOrderRequest) {
        switch (code) {
            case 200:
                return String.format("Success!!!... Item %s checked out by customer with quantity %d\nYour Bill is LKR: %.2f", placeOrderRequest.getItemCode(), placeOrderRequest.getQuantity(), finalTotal);
            case 404:
                return "Invalid input!!!... No such item to check out!";
            case 401:
                return String.format("Sorry!!!... Not enough items to check out from %s", placeOrderRequest.getItemCode());
            default:
                return "Unexpected error occurred during the transaction.";
        }
    }

    private PlaceOrderResponse callServer(String itemCode, int quantity, boolean isSentByPrimary, String ipAddress, int port) {
        System.out.println("Call Server " + ipAddress + ":" + port);
        ManagedChannel channel = ManagedChannelBuilder.forAddress(ipAddress, port).usePlaintext().build();
        clientStub = PlaceOrderServiceGrpc.newBlockingStub(channel);

        PlaceOrderRequest request = PlaceOrderRequest.newBuilder().setItemCode(itemCode).setQuantity(quantity).setIsSentByPrimary(isSentByPrimary).build();
        return clientStub.placeOrder(request);
    }

    private PlaceOrderResponse callPrimary(String itemCode, int quantity) {
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

    private void startDistributedTx(PlaceOrderRequest request) {
        try {
            DistributedTx transaction = inventoryControlServer.getTransaction();
            transaction.start(request.getItemCode(), String.valueOf(UUID.randomUUID()));
            placeOrderRequest = request;
        } catch (IOException ignored) {
        }
    }

    @Override
    public void onGlobalCommit() {
        placeOrder();
    }

    @Override
    public void onGlobalAbort() {
        placeOrderRequest = null;
        System.out.println("Transaction Aborted by the Coordinator");
    }
}
