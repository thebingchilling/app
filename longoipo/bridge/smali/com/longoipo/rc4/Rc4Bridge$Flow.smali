.class final Lcom/longoipo/rc4/Rc4Bridge$Flow;
.super Ljava/lang/Object;
.source "Rc4Bridge.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/longoipo/rc4/Rc4Bridge;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x12
    name = "Flow"
.end annotation


# instance fields
.field private final client:Ljava/net/SocketAddress;

.field private volatile lastUsed:J

.field final synthetic this$0:Lcom/longoipo/rc4/Rc4Bridge;

.field private final up:Ljava/net/DatagramSocket;


# direct methods
.method constructor <init>(Lcom/longoipo/rc4/Rc4Bridge;Ljava/net/SocketAddress;)V
    .registers 5
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x1010,
            0x0
        }
        names = {
            null,
            null
        }
    .end annotation

    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 318
    iput-object p1, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->this$0:Lcom/longoipo/rc4/Rc4Bridge;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    .line 316
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->lastUsed:J

    .line 319
    iput-object p2, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->client:Ljava/net/SocketAddress;

    .line 320
    new-instance p2, Ljava/net/DatagramSocket;

    invoke-direct {p2}, Ljava/net/DatagramSocket;-><init>()V

    iput-object p2, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->up:Ljava/net/DatagramSocket;

    .line 321
    new-instance p0, Ljava/net/InetSocketAddress;

    # getter for: Lcom/longoipo/rc4/Rc4Bridge;->host:Ljava/lang/String;
    invoke-static {p1}, Lcom/longoipo/rc4/Rc4Bridge;->access$400(Lcom/longoipo/rc4/Rc4Bridge;)Ljava/lang/String;

    move-result-object v0

    # getter for: Lcom/longoipo/rc4/Rc4Bridge;->port:I
    invoke-static {p1}, Lcom/longoipo/rc4/Rc4Bridge;->access$500(Lcom/longoipo/rc4/Rc4Bridge;)I

    move-result p1

    invoke-direct {p0, v0, p1}, Ljava/net/InetSocketAddress;-><init>(Ljava/lang/String;I)V

    invoke-virtual {p2, p0}, Ljava/net/DatagramSocket;->connect(Ljava/net/SocketAddress;)V

    const/16 p0, 0x3e8

    .line 322
    invoke-virtual {p2, p0}, Ljava/net/DatagramSocket;->setSoTimeout(I)V

    return-void
.end method


# virtual methods
.method readLoop()V
    .registers 9

    const v0, 0xffff

    .line 337
    new-array v1, v0, [B

    .line 338
    new-instance v2, Ljava/net/DatagramPacket;

    invoke-direct {v2, v1, v0}, Ljava/net/DatagramPacket;-><init>([BI)V

    .line 340
    :catch_a
    :goto_a
    :try_start_a
    iget-object v3, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->up:Ljava/net/DatagramSocket;

    invoke-virtual {v3}, Ljava/net/DatagramSocket;->isClosed()Z

    move-result v3

    if-nez v3, :cond_69

    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v3

    iget-wide v5, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->lastUsed:J
    :try_end_18
    .catch Ljava/io/IOException; {:try_start_a .. :try_end_18} :catch_69
    .catchall {:try_start_a .. :try_end_18} :catchall_57

    sub-long/2addr v3, v5

    const-wide/32 v5, 0xea60

    cmp-long v3, v3, v5

    if-gez v3, :cond_69

    .line 342
    :try_start_20
    invoke-virtual {v2, v0}, Ljava/net/DatagramPacket;->setLength(I)V

    .line 343
    iget-object v3, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->up:Ljava/net/DatagramSocket;

    invoke-virtual {v3, v2}, Ljava/net/DatagramSocket;->receive(Ljava/net/DatagramPacket;)V
    :try_end_28
    .catch Ljava/net/SocketTimeoutException; {:try_start_20 .. :try_end_28} :catch_a
    .catch Ljava/io/IOException; {:try_start_20 .. :try_end_28} :catch_69
    .catchall {:try_start_20 .. :try_end_28} :catchall_57

    .line 347
    :try_start_28
    invoke-virtual {v2}, Ljava/net/DatagramPacket;->getLength()I

    move-result v3

    const/16 v4, 0x10

    sub-int/2addr v3, v4

    if-gtz v3, :cond_32

    goto :goto_a

    .line 349
    :cond_32
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v5

    iput-wide v5, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->lastUsed:J

    .line 350
    iget-object v5, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->this$0:Lcom/longoipo/rc4/Rc4Bridge;

    # getter for: Lcom/longoipo/rc4/Rc4Bridge;->masterKey:[B
    invoke-static {v5}, Lcom/longoipo/rc4/Rc4Bridge;->access$700(Lcom/longoipo/rc4/Rc4Bridge;)[B

    move-result-object v5

    const/4 v6, 0x0

    invoke-static {v5, v1, v6}, Lcom/longoipo/rc4/Rc4Md5;->stream([B[BI)Lcom/longoipo/rc4/Rc4Md5$Rc4;

    move-result-object v5

    invoke-virtual {v5, v1, v4, v3}, Lcom/longoipo/rc4/Rc4Md5$Rc4;->crypt([BII)V

    .line 351
    iget-object v5, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->this$0:Lcom/longoipo/rc4/Rc4Bridge;

    # getter for: Lcom/longoipo/rc4/Rc4Bridge;->udp:Ljava/net/DatagramSocket;
    invoke-static {v5}, Lcom/longoipo/rc4/Rc4Bridge;->access$800(Lcom/longoipo/rc4/Rc4Bridge;)Ljava/net/DatagramSocket;

    move-result-object v5

    new-instance v6, Ljava/net/DatagramPacket;

    iget-object v7, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->client:Ljava/net/SocketAddress;

    invoke-direct {v6, v1, v4, v3, v7}, Ljava/net/DatagramPacket;-><init>([BIILjava/net/SocketAddress;)V

    invoke-virtual {v5, v6}, Ljava/net/DatagramSocket;->send(Ljava/net/DatagramPacket;)V
    :try_end_56
    .catch Ljava/io/IOException; {:try_start_28 .. :try_end_56} :catch_69
    .catchall {:try_start_28 .. :try_end_56} :catchall_57

    goto :goto_a

    :catchall_57
    move-exception v0

    .line 356
    iget-object v1, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->up:Ljava/net/DatagramSocket;

    invoke-virtual {v1}, Ljava/net/DatagramSocket;->close()V

    .line 357
    iget-object v1, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->this$0:Lcom/longoipo/rc4/Rc4Bridge;

    # getter for: Lcom/longoipo/rc4/Rc4Bridge;->flows:Ljava/util/concurrent/ConcurrentHashMap;
    invoke-static {v1}, Lcom/longoipo/rc4/Rc4Bridge;->access$900(Lcom/longoipo/rc4/Rc4Bridge;)Ljava/util/concurrent/ConcurrentHashMap;

    move-result-object v1

    iget-object v2, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->client:Ljava/net/SocketAddress;

    invoke-virtual {v1, v2, p0}, Ljava/util/concurrent/ConcurrentHashMap;->remove(Ljava/lang/Object;Ljava/lang/Object;)Z

    .line 358
    throw v0

    .line 356
    :catch_69
    :cond_69
    iget-object v0, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->up:Ljava/net/DatagramSocket;

    invoke-virtual {v0}, Ljava/net/DatagramSocket;->close()V

    .line 357
    iget-object v0, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->this$0:Lcom/longoipo/rc4/Rc4Bridge;

    # getter for: Lcom/longoipo/rc4/Rc4Bridge;->flows:Ljava/util/concurrent/ConcurrentHashMap;
    invoke-static {v0}, Lcom/longoipo/rc4/Rc4Bridge;->access$900(Lcom/longoipo/rc4/Rc4Bridge;)Ljava/util/concurrent/ConcurrentHashMap;

    move-result-object v0

    iget-object v1, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->client:Ljava/net/SocketAddress;

    invoke-virtual {v0, v1, p0}, Ljava/util/concurrent/ConcurrentHashMap;->remove(Ljava/lang/Object;Ljava/lang/Object;)Z

    return-void
.end method

.method sendToServer([BI)V
    .registers 8
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 326
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->lastUsed:J

    add-int/lit8 v0, p2, 0x10

    .line 327
    new-array v1, v0, [B

    const/16 v2, 0x10

    .line 328
    new-array v3, v2, [B

    .line 329
    # getter for: Lcom/longoipo/rc4/Rc4Bridge;->RANDOM:Ljava/security/SecureRandom;
    invoke-static {}, Lcom/longoipo/rc4/Rc4Bridge;->access$600()Ljava/security/SecureRandom;

    move-result-object v4

    invoke-virtual {v4, v3}, Ljava/security/SecureRandom;->nextBytes([B)V

    const/4 v4, 0x0

    .line 330
    invoke-static {v3, v4, v1, v4, v2}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V

    .line 331
    invoke-static {p1, v4, v1, v2, p2}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V

    .line 332
    iget-object p1, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->this$0:Lcom/longoipo/rc4/Rc4Bridge;

    # getter for: Lcom/longoipo/rc4/Rc4Bridge;->masterKey:[B
    invoke-static {p1}, Lcom/longoipo/rc4/Rc4Bridge;->access$700(Lcom/longoipo/rc4/Rc4Bridge;)[B

    move-result-object p1

    invoke-static {p1, v3, v4}, Lcom/longoipo/rc4/Rc4Md5;->stream([B[BI)Lcom/longoipo/rc4/Rc4Md5$Rc4;

    move-result-object p1

    invoke-virtual {p1, v1, v2, p2}, Lcom/longoipo/rc4/Rc4Md5$Rc4;->crypt([BII)V

    .line 333
    iget-object p0, p0, Lcom/longoipo/rc4/Rc4Bridge$Flow;->up:Ljava/net/DatagramSocket;

    new-instance p1, Ljava/net/DatagramPacket;

    invoke-direct {p1, v1, v0}, Ljava/net/DatagramPacket;-><init>([BI)V

    invoke-virtual {p0, p1}, Ljava/net/DatagramSocket;->send(Ljava/net/DatagramPacket;)V

    return-void
.end method
