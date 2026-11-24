package com.badlogic.game.net;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/** Funções utilitárias de rede. */
public final class NetUtils {

    private NetUtils() {
    }

    /**
     * Lista os endereços IPv4 desta máquina na rede local (ex.: 192.168.0.15).
     * O host mostra esses IPs na sala de espera para os colegas saberem onde conectar.
     * Ignora o endereço de loopback (127.0.0.1), que só funciona na própria máquina.
     */
    public static List<String> getLocalIPv4Addresses() {
        List<String> result = new ArrayList<String>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address) {
                        result.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            // sem permissão para listar as interfaces: devolve a lista vazia
        }
        return result;
    }
}
