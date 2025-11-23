package com.github.auties00.cobalt.socket.call;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.model.call.CallBuilder;
import com.github.auties00.cobalt.model.call.CallStatus;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.socket.SocketStream;
import com.github.auties00.cobalt.util.Clock;
import com.github.auties00.cobalt.util.SecureBytes;
import com.github.auties00.cobalt.util.voice.WhatsappVoice;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Base64;

public final class CallStreamNodeHandler extends SocketStream.Handler {
    public CallStreamNodeHandler(WhatsAppClient whatsapp) {
        super(whatsapp, "call");
    }

    @Override
    public void handle(Node node) {
        whatsapp.sendAck(node);
        var callNode = node.getChild();
        if (callNode.isEmpty()) {
            return;
        }

        // TODO: Support other types
        switch (callNode.get().description()) {
            case "offer" -> handleOffer(node, callNode.get());
        }
    }
    private void handleOffer(Node infoNode, Node callNode) {
        var from = infoNode.getRequiredAttributeAsJid("from");
        var callId = callNode.getRequiredAttributeAsString("call-id");
        var caller = callNode.getAttributeAsJid("call-creator", from);
        var status = getCallStatus(callNode);
        var timestampSeconds = callNode.getAttributeAsLong("t")
                .orElseGet(Clock::nowSeconds);
        var isOffline = callNode.hasAttribute("offline");
        var hasVideo = callNode.hasChild("video");
        var call = new CallBuilder()
                .chatJid(from)
                .callerJid(caller)
                .id(callId)
                .timestampSeconds(timestampSeconds)
                .video(hasVideo)
                .status(status)
                .offline(isOffline)
                .build();
        whatsapp.store().addCall(call);
        for(var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onCall(whatsapp, call));
        }
        sendReceipt(infoNode, callNode, from, callId);
        sendPreaccept(from, callId);
        var relayParam = sendRelaylatency(from, callId, callNode);
        if (relayParam != null) {
            startVoiceRelay(relayParam, null, from, callId);
        }
    }

    private CallStatus getCallStatus(Node node) {
        return switch (node.description()) {
            case "terminate" -> node.hasAttribute("reason", "timeout") ? CallStatus.TIMED_OUT : CallStatus.REJECTED;
            case "reject" -> CallStatus.REJECTED;
            case "accept" -> CallStatus.ACCEPTED;
            default -> CallStatus.RINGING;
        };
    }
    private void sendReceipt(Node infoNode, Node callNode, Jid from, String callId) {
        // Create offer child node
        var offer = new NodeBuilder()
                .description("offer")
                .attribute("call-id", callId)  // Replace with actual call_id
                .attribute("call-creator", callNode.getAttributeAsJid("call-creator", from))  // Replace with actual call_creator
                .build();

        // Create receipt node with offer child
        var receipt = new NodeBuilder()
                .description("receipt")
                .attribute("id", infoNode.getRequiredAttributeAsString("id"))
                .attribute("to", from)
                .content(offer);
        whatsapp.sendNodeWithNoResponse(receipt.build());
    }

    private void sendPreaccept(Jid from, String callId) {
        // Create audio child node
        var audio = new NodeBuilder()
                .description("audio")
                .attribute("rate", "16000")
                .attribute("enc", "opus")
                .build();

        // Create encopt child node
        var encopt = new NodeBuilder()
                .description("encopt")
                .attribute("keygen", "2")
                .build();

        // Create capability child node with base64 data
        var capability = new NodeBuilder()
                .description("capability")
                .attribute("ver", "1")
                .content(java.util.Base64.getDecoder().decode("AQT3CcT6"))  // Equivalent to QByteArray("AQT3CcT6")
                .build();

        // Create preaccept node with all child nodes
        var preaccept = new NodeBuilder()
                .description("preaccept")
                .attribute("call-creator", from)
                .attribute("call-id", callId)
                .content(audio, encopt, capability)  // Add video, audio, encopt, capability as children
                .build();

        // Create call node with preaccept child
        var call = new NodeBuilder()
                .description("call")
                .attribute("id", SecureBytes.randomHex(10))
                .attribute("to", from)
                .content(preaccept);

        // Send the node
        whatsapp.sendNodeWithNoResponse(call.build());
    }

    private RelayParam sendRelaylatency(Jid from, String callId, Node callNode) {
        var relayNode = callNode.getChild("relay");
        if (relayNode.isEmpty()) {
            System.err.println("No relay node found in call");
            return null;
        }
        
        var relay = relayNode.get();
        
        // Get te2 children (equivalent to auto te2 = relay.GetChildren("te2"))
        var te2Nodes = relay.getChildren("te2");
        
        // Get token children (equivalent to auto token = relay.GetChildren("token"))
        var tokenNodes = relay.getChildren("token");
        
        // Find turn_token from token nodes
        byte[] turnToken = null;
        for (var token : tokenNodes) {
            var turnId = token.getAttributeAsString("id", "");
            if ("0".equals(turnId)) {
                turnToken = token.toContentBytes().orElse(null);
                break;
            }
        }
        
        // Find turn_ip from te2 nodes
        byte[] turnIp = null;
        for (var te2 : te2Nodes) {
            var ipBytes = te2.toContentBytes().orElse(null);
            if (ipBytes != null && ipBytes.length == 6) {
                var tokenId = te2.getAttributeAsString("token_id", "");
                if ("0".equals(tokenId)) {
                    turnIp = ipBytes;
                    break;
                }
            }
        }
        
        // Check if we have both turn_token and turn_ip
        if (turnToken == null || turnIp == null) {
            System.err.println("Parse turn token or turn ip failed");
            return null;
        }
        
        // Generate random latency (equivalent to 33554 + random(500-700))
        var random = new java.security.SecureRandom();
        var randomLatency = 500 + random.nextInt(200); // 500 to 699
        var latencyValue = "33554" + randomLatency;
        
        // Create te node
        var te = new NodeBuilder()
                .description("te")
                .attribute("latency", latencyValue)
                .content(turnIp)
                .build();
        
        // Create relaylatency node
        var relaylatency = new NodeBuilder()
                .description("relaylatency")
                .attribute("call-creator", from)
                .attribute("call-id", callId)
                .content(te)
                .build();
        
        // Create call node
        var call = new NodeBuilder()
                .description("call")
                .attribute("to", from)
                .attribute("id", SecureBytes.randomHex(10))
                .content(relaylatency);
        
        // Send the node
        whatsapp.sendNode(call);
        return new RelayParam(turnIp, turnToken, relay.getChild("key").get().toContentString().get());
    }

    /**
     * Parses IP address and port from binary data
     * Equivalent to C++ WamEventUtil::IpParse function
     * 
     * @param turnIp binary data containing IP address and port
     * @return IpPort object containing the parsed IP address and port, or null if parsing fails
     */
    public static class IpPort {
        public final String ip;
        public final int port;
        
        public IpPort(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }
        
        @Override
        public String toString() {
            return ip + ":" + port;
        }
    }

    public static class RelayParam {
        public byte[] turnIp;
        public byte[] turnToken;
        public String key;

        public RelayParam(byte[] turnIp, byte[] turnToken, String key) {
            this.turnIp = turnIp;
            this.turnToken = turnToken;
            this.key = key;
        }
    }
    
    public static IpPort parseIp(byte[] turnIp) {
        if (turnIp == null) {
            return null;
        }
        
        if (turnIp.length == 6) {
            // IPv4: 4 bytes IP + 2 bytes port
            try {
                // Extract IP address (first 4 bytes)
                var ipBytes = new byte[4];
                System.arraycopy(turnIp, 0, ipBytes, 0, 4);
                var ipAddress = InetAddress.getByAddress(ipBytes);
                
                // Extract port (last 2 bytes) - network byte order (big-endian)
                var portBuffer = ByteBuffer.wrap(turnIp, 4, 2);
                var port = portBuffer.getShort() & 0xFFFF; // Convert to unsigned
                
                return new IpPort(ipAddress.getHostAddress(), port);
                
            } catch (UnknownHostException e) {
                System.err.println("Failed to parse IPv4 address: " + Base64.getEncoder().encodeToString(turnIp));
                return null;
            }
            
        } else if (turnIp.length == 18) {
            // IPv6: 16 bytes IP + 2 bytes port
            try {
                // Extract IP address (first 16 bytes)
                var ipBytes = new byte[16];
                System.arraycopy(turnIp, 0, ipBytes, 0, 16);
                var ipAddress = InetAddress.getByAddress(ipBytes);
                
                // Extract port (last 2 bytes) - network byte order (big-endian)
                var portBuffer = ByteBuffer.wrap(turnIp, 16, 2);
                var port = portBuffer.getShort() & 0xFFFF; // Convert to unsigned
                
                return new IpPort(ipAddress.getHostAddress(), port);
                
            } catch (UnknownHostException e) {
                System.err.println("Failed to parse IPv6 address: " + Base64.getEncoder().encodeToString(turnIp));
                return null;
            }
            
        } else {
            System.err.println("IP error: " + Base64.getEncoder().encodeToString(turnIp));
            return null;
        }
    }

    private void startVoiceRelay(RelayParam relayParam, byte[]callKey, Jid from, String callId) {
        var ip = parseIp(relayParam.turnIp);
        //whatsapp.store().jid();
        new WhatsappVoice(ip.ip, ip.port, relayParam.turnToken, callKey, whatsapp.store().jid().get().toString(), from.toString(), callId, relayParam.key);
    }
}