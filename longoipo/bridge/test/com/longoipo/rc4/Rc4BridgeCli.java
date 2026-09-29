package com.longoipo.rc4;

/** Test helper: starts one bridge and prints its local port. Usage: Rc4BridgeCli host port password */
public final class Rc4BridgeCli {
    public static void main(String[] args) throws Exception {
        Rc4Bridge b = Rc4Bridge.open(args[0], Integer.parseInt(args[1]), args[2]);
        System.out.println("PORT " + b.getPort());
        System.out.flush();
        Thread.sleep(Long.MAX_VALUE);
    }
}
