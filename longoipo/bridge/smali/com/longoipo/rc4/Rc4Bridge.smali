.class public final Lcom/longoipo/rc4/Rc4Bridge;
.super Ljava/lang/Object;
.source "Rc4Bridge.java"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/longoipo/rc4/Rc4Bridge$Flow;
    }
.end annotation


# static fields
.field private static final BRIDGES:Ljava/util/Map;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Lcom/longoipo/rc4/Rc4Bridge;",
            ">;"
        }
    .end annotation
.end field

.field private static final MAX_UDP_FLOWS:I = 0x400

.field private static final POOL:Ljava/util/concurrent/ExecutorService;

.field private static final RANDOM:Ljava/security/SecureRandom;

.field private static final TAG:Ljava/lang/String; = "[Rc4Bridge] "

.field private static final UDP_IDLE_MS:J = 0xea60L


# instance fields
.field private final flows:Ljava/util/concurrent/ConcurrentHashMap;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/concurrent/ConcurrentHashMap<",
            "Ljava/net/SocketAddress;",
            "Lcom/longoipo/rc4/Rc4Bridge$Flow;",
            ">;"
        }
    .end annotation
.end field

.field private final host:Ljava/lang/String;

.field private final masterKey:[B

.field private final port:I

.field private tcp:Ljava/net/ServerSocket;

.field private udp:Ljava/net/DatagramSocket;


# direct methods
.method static constructor <clinit>()V
    .registers 1

    .line 43
    new-instance v0, Ljava/util/HashMap;

    invoke-direct {v0}, Ljava/util/HashMap;-><init>()V

    sput-object v0, Lcom/longoipo/rc4/Rc4Bridge;->BRIDGES:Ljava/util/Map;

    .line 44
    new-instance v0, Ljava/security/SecureRandom;

    invoke-direct {v0}, Ljava/security/SecureRandom;-><init>()V

    sput-object v0, Lcom/longoipo/rc4/Rc4Bridge;->RANDOM:Ljava/security/SecureRandom;

    .line 45
    new-instance v0, Lcom/longoipo/rc4/Rc4Bridge$1;

    invoke-direct {v0}, Lcom/longoipo/rc4/Rc4Bridge$1;-><init>()V

    invoke-static {v0}, Ljava/util/concurrent/Executors;->newCachedThreadPool(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;

    move-result-object v0

    sput-object v0, Lcom/longoipo/rc4/Rc4Bridge;->POOL:Ljava/util/concurrent/ExecutorService;

    return-void
.end method

.method private constructor <init>(Ljava/lang/String;ILjava/lang/String;)V
    .registers 5

    .line 61
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    .line 59
    new-instance v0, Ljava/util/concurrent/ConcurrentHashMap;

    invoke-direct {v0}, Ljava/util/concurrent/ConcurrentHashMap;-><init>()V

    iput-object v0, p0, Lcom/longoipo/rc4/Rc4Bridge;->flows:Ljava/util/concurrent/ConcurrentHashMap;

    .line 62
    iput-object p1, p0, Lcom/longoipo/rc4/Rc4Bridge;->host:Ljava/lang/String;

    .line 63
    iput p2, p0, Lcom/longoipo/rc4/Rc4Bridge;->port:I

    .line 64
    sget-object p1, Ljava/nio/charset/StandardCharsets;->UTF_8:Ljava/nio/charset/Charset;

    invoke-virtual {p3, p1}, Ljava/lang/String;->getBytes(Ljava/nio/charset/Charset;)[B

    move-result-object p1

    invoke-static {p1}, Lcom/longoipo/rc4/Rc4Md5;->deriveKey([B)[B

    move-result-object p1

    iput-object p1, p0, Lcom/longoipo/rc4/Rc4Bridge;->masterKey:[B

    return-void
.end method

.method private acceptLoop()V
    .registers 4

    .line 185
    :cond_0
    :goto_0
    iget-object v0, p0, Lcom/longoipo/rc4/Rc4Bridge;->tcp:Ljava/net/ServerSocket;

    invoke-virtual {v0}, Ljava/net/ServerSocket;->isClosed()Z

    move-result v0

    if-nez v0, :cond_35

    .line 187
    :try_start_8
    iget-object v0, p0, Lcom/longoipo/rc4/Rc4Bridge;->tcp:Ljava/net/ServerSocket;

    invoke-virtual {v0}, Ljava/net/ServerSocket;->accept()Ljava/net/Socket;

    move-result-object v0

    .line 188
    sget-object v1, Lcom/longoipo/rc4/Rc4Bridge;->POOL:Ljava/util/concurrent/ExecutorService;

    new-instance v2, Lcom/longoipo/rc4/Rc4Bridge$4;

    invoke-direct {v2, p0, v0}, Lcom/longoipo/rc4/Rc4Bridge$4;-><init>(Lcom/longoipo/rc4/Rc4Bridge;Ljava/net/Socket;)V

    invoke-interface {v1, v2}, Ljava/util/concurrent/ExecutorService;->execute(Ljava/lang/Runnable;)V
    :try_end_18
    .catch Ljava/io/IOException; {:try_start_8 .. :try_end_18} :catch_19

    goto :goto_0

    :catch_19
    move-exception v0

    .line 195
    iget-object v1, p0, Lcom/longoipo/rc4/Rc4Bridge;->tcp:Ljava/net/ServerSocket;

    invoke-virtual {v1}, Ljava/net/ServerSocket;->isClosed()Z

    move-result v1

    if-nez v1, :cond_0

    new-instance v1, Ljava/lang/StringBuilder;

    const-string v2, "accept failed: "

    invoke-direct {v1, v2}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v0}, Lcom/longoipo/rc4/Rc4Bridge;->warn(Ljava/lang/String;)V

    goto :goto_0

    :cond_35
    return-void
.end method

.method static synthetic access$000(Lcom/longoipo/rc4/Rc4Bridge;)V
    .registers 1

    .line 38
    invoke-direct {p0}, Lcom/longoipo/rc4/Rc4Bridge;->acceptLoop()V

    return-void
.end method

.method static synthetic access$100(Lcom/longoipo/rc4/Rc4Bridge;)V
    .registers 1

    .line 38
    invoke-direct {p0}, Lcom/longoipo/rc4/Rc4Bridge;->udpLoop()V

    return-void
.end method

.method static synthetic access$200(Lcom/longoipo/rc4/Rc4Bridge;Ljava/net/Socket;)V
    .registers 2

    .line 38
    invoke-direct {p0, p1}, Lcom/longoipo/rc4/Rc4Bridge;->handleTcp(Ljava/net/Socket;)V

    return-void
.end method

.method static synthetic access$300(Lcom/longoipo/rc4/Rc4Bridge;Ljava/net/Socket;Ljava/net/Socket;)V
    .registers 3

    .line 38
    invoke-direct {p0, p1, p2}, Lcom/longoipo/rc4/Rc4Bridge;->pumpToServer(Ljava/net/Socket;Ljava/net/Socket;)V

    return-void
.end method

.method static synthetic access$400(Lcom/longoipo/rc4/Rc4Bridge;)Ljava/lang/String;
    .registers 1

    .line 38
    iget-object p0, p0, Lcom/longoipo/rc4/Rc4Bridge;->host:Ljava/lang/String;

    return-object p0
.end method

.method static synthetic access$500(Lcom/longoipo/rc4/Rc4Bridge;)I
    .registers 1

    .line 38
    iget p0, p0, Lcom/longoipo/rc4/Rc4Bridge;->port:I

    return p0
.end method

.method static synthetic access$600()Ljava/security/SecureRandom;
    .registers 1

    .line 38
    sget-object v0, Lcom/longoipo/rc4/Rc4Bridge;->RANDOM:Ljava/security/SecureRandom;

    return-object v0
.end method

.method static synthetic access$700(Lcom/longoipo/rc4/Rc4Bridge;)[B
    .registers 1

    .line 38
    iget-object p0, p0, Lcom/longoipo/rc4/Rc4Bridge;->masterKey:[B

    return-object p0
.end method

.method static synthetic access$800(Lcom/longoipo/rc4/Rc4Bridge;)Ljava/net/DatagramSocket;
    .registers 1

    .line 38
    iget-object p0, p0, Lcom/longoipo/rc4/Rc4Bridge;->udp:Ljava/net/DatagramSocket;

    return-object p0
.end method

.method static synthetic access$900(Lcom/longoipo/rc4/Rc4Bridge;)Ljava/util/concurrent/ConcurrentHashMap;
    .registers 1

    .line 38
    iget-object p0, p0, Lcom/longoipo/rc4/Rc4Bridge;->flows:Ljava/util/concurrent/ConcurrentHashMap;

    return-object p0
.end method

.method private static closeQuietly(Ljava/net/Socket;)V
    .registers 1

    if-eqz p0, :cond_5

    .line 366
    :try_start_2
    invoke-virtual {p0}, Ljava/net/Socket;->close()V
    :try_end_5
    .catch Ljava/io/IOException; {:try_start_2 .. :try_end_5} :catch_5

    :catch_5
    :cond_5
    return-void
.end method

.method private handleTcp(Ljava/net/Socket;)V
    .registers 7

    .line 201
    new-instance v0, Ljava/net/Socket;

    invoke-direct {v0}, Ljava/net/Socket;-><init>()V

    const/4 v1, 0x1

    .line 203
    :try_start_6
    invoke-virtual {p1, v1}, Ljava/net/Socket;->setTcpNoDelay(Z)V

    .line 204
    new-instance v2, Ljava/net/InetSocketAddress;

    iget-object v3, p0, Lcom/longoipo/rc4/Rc4Bridge;->host:Ljava/lang/String;

    iget v4, p0, Lcom/longoipo/rc4/Rc4Bridge;->port:I

    invoke-direct {v2, v3, v4}, Ljava/net/InetSocketAddress;-><init>(Ljava/lang/String;I)V

    const/16 v3, 0x2710

    invoke-virtual {v0, v2, v3}, Ljava/net/Socket;->connect(Ljava/net/SocketAddress;I)V

    .line 205
    invoke-virtual {v0, v1}, Ljava/net/Socket;->setTcpNoDelay(Z)V

    .line 207
    sget-object v1, Lcom/longoipo/rc4/Rc4Bridge;->POOL:Ljava/util/concurrent/ExecutorService;

    new-instance v2, Lcom/longoipo/rc4/Rc4Bridge$5;

    invoke-direct {v2, p0, p1, v0}, Lcom/longoipo/rc4/Rc4Bridge$5;-><init>(Lcom/longoipo/rc4/Rc4Bridge;Ljava/net/Socket;Ljava/net/Socket;)V

    invoke-interface {v1, v2}, Ljava/util/concurrent/ExecutorService;->submit(Ljava/lang/Runnable;)Ljava/util/concurrent/Future;

    move-result-object v1

    .line 213
    invoke-direct {p0, p1, v0}, Lcom/longoipo/rc4/Rc4Bridge;->pumpToClient(Ljava/net/Socket;Ljava/net/Socket;)V

    .line 214
    sget-object p0, Ljava/util/concurrent/TimeUnit;->SECONDS:Ljava/util/concurrent/TimeUnit;

    const-wide/16 v2, 0x3c

    invoke-interface {v1, v2, v3, p0}, Ljava/util/concurrent/Future;->get(JLjava/util/concurrent/TimeUnit;)Ljava/lang/Object;
    :try_end_2f
    .catchall {:try_start_6 .. :try_end_2f} :catchall_2f

    .line 218
    :catchall_2f
    invoke-static {p1}, Lcom/longoipo/rc4/Rc4Bridge;->closeQuietly(Ljava/net/Socket;)V

    .line 219
    invoke-static {v0}, Lcom/longoipo/rc4/Rc4Bridge;->closeQuietly(Ljava/net/Socket;)V

    return-void
.end method

.method private isAlive()Z
    .registers 1

    .line 143
    iget-object p0, p0, Lcom/longoipo/rc4/Rc4Bridge;->tcp:Ljava/net/ServerSocket;

    if-eqz p0, :cond_c

    invoke-virtual {p0}, Ljava/net/ServerSocket;->isClosed()Z

    move-result p0

    if-nez p0, :cond_c

    const/4 p0, 0x1

    return p0

    :cond_c
    const/4 p0, 0x0

    return p0
.end method

.method private static isPlainTcp(Lorg/json/JSONObject;)Z
    .registers 6

    const/4 v0, 0x1

    if-nez p0, :cond_4

    return v0

    .line 109
    :cond_4
    const-string v1, "network"

    const-string v2, "tcp"

    invoke-virtual {p0, v1, v2}, Lorg/json/JSONObject;->optString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    .line 110
    invoke-virtual {v1}, Ljava/lang/String;->isEmpty()Z

    move-result v3

    const/4 v4, 0x0

    if-nez v3, :cond_22

    invoke-virtual {v1, v2}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z

    move-result v2

    if-nez v2, :cond_22

    const-string v2, "raw"

    invoke-virtual {v1, v2}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z

    move-result v1

    if-nez v1, :cond_22

    return v4

    .line 111
    :cond_22
    const-string v1, "security"

    const-string v2, "none"

    invoke-virtual {p0, v1, v2}, Lorg/json/JSONObject;->optString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    .line 112
    invoke-virtual {v1}, Ljava/lang/String;->isEmpty()Z

    move-result v3

    if-nez v3, :cond_37

    invoke-virtual {v1, v2}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z

    move-result v1

    if-nez v1, :cond_37

    return v4

    .line 113
    :cond_37
    const-string v1, "tcpSettings"

    invoke-virtual {p0, v1}, Lorg/json/JSONObject;->optJSONObject(Ljava/lang/String;)Lorg/json/JSONObject;

    move-result-object p0

    if-eqz p0, :cond_5a

    .line 115
    const-string v1, "header"

    invoke-virtual {p0, v1}, Lorg/json/JSONObject;->optJSONObject(Ljava/lang/String;)Lorg/json/JSONObject;

    move-result-object p0

    if-eqz p0, :cond_5a

    .line 117
    const-string v1, "type"

    invoke-virtual {p0, v1, v2}, Lorg/json/JSONObject;->optString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object p0

    .line 118
    invoke-virtual {p0}, Ljava/lang/String;->isEmpty()Z

    move-result v1

    if-nez v1, :cond_5a

    invoke-virtual {p0, v2}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z

    move-result p0

    if-nez p0, :cond_5a

    return v4

    :cond_5a
    return v0
.end method

.method public static declared-synchronized open(Ljava/lang/String;ILjava/lang/String;)Lcom/longoipo/rc4/Rc4Bridge;
    .registers 8

    const-class v0, Lcom/longoipo/rc4/Rc4Bridge;

    monitor-enter v0

    .line 128
    :try_start_3
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v1, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    const-string v2, "|"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v1

    const-string v2, "|"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    .line 129
    sget-object v2, Lcom/longoipo/rc4/Rc4Bridge;->BRIDGES:Ljava/util/Map;

    invoke-interface {v2, v1}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Lcom/longoipo/rc4/Rc4Bridge;

    if-eqz v3, :cond_34

    .line 130
    invoke-direct {v3}, Lcom/longoipo/rc4/Rc4Bridge;->isAlive()Z

    move-result v4

    if-nez v4, :cond_3f

    .line 131
    :cond_34
    new-instance v3, Lcom/longoipo/rc4/Rc4Bridge;

    invoke-direct {v3, p0, p1, p2}, Lcom/longoipo/rc4/Rc4Bridge;-><init>(Ljava/lang/String;ILjava/lang/String;)V

    .line 132
    invoke-direct {v3}, Lcom/longoipo/rc4/Rc4Bridge;->start()V

    .line 133
    invoke-interface {v2, v1, v3}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    :try_end_3f
    .catchall {:try_start_3 .. :try_end_3f} :catchall_41

    .line 135
    :cond_3f
    monitor-exit v0

    return-object v3

    :catchall_41
    move-exception p0

    :try_start_42
    monitor-exit v0
    :try_end_43
    .catchall {:try_start_42 .. :try_end_43} :catchall_41

    throw p0
.end method

.method private pumpToClient(Ljava/net/Socket;Ljava/net/Socket;)V
    .registers 8

    .line 254
    :try_start_0
    invoke-virtual {p2}, Ljava/net/Socket;->getInputStream()Ljava/io/InputStream;

    move-result-object v0

    .line 255
    invoke-virtual {p1}, Ljava/net/Socket;->getOutputStream()Ljava/io/OutputStream;

    move-result-object v1

    const/16 v2, 0x10

    .line 256
    new-array v2, v2, [B

    .line 257
    invoke-static {v0, v2}, Lcom/longoipo/rc4/Rc4Bridge;->readFully(Ljava/io/InputStream;[B)Z

    move-result v3

    if-nez v3, :cond_13

    return-void

    .line 258
    :cond_13
    iget-object p0, p0, Lcom/longoipo/rc4/Rc4Bridge;->masterKey:[B

    const/4 v3, 0x0

    invoke-static {p0, v2, v3}, Lcom/longoipo/rc4/Rc4Md5;->stream([B[BI)Lcom/longoipo/rc4/Rc4Md5$Rc4;

    move-result-object p0

    const/16 v2, 0x4000

    .line 259
    new-array v2, v2, [B

    .line 261
    :goto_1e
    invoke-virtual {v0, v2}, Ljava/io/InputStream;->read([B)I

    move-result v4

    if-ltz v4, :cond_2b

    .line 262
    invoke-virtual {p0, v2, v3, v4}, Lcom/longoipo/rc4/Rc4Md5$Rc4;->crypt([BII)V

    .line 263
    invoke-virtual {v1, v2, v3, v4}, Ljava/io/OutputStream;->write([BII)V

    goto :goto_1e

    .line 265
    :cond_2b
    invoke-virtual {p1}, Ljava/net/Socket;->shutdownOutput()V
    :try_end_2e
    .catch Ljava/io/IOException; {:try_start_0 .. :try_end_2e} :catch_2f

    return-void

    .line 267
    :catch_2f
    invoke-static {p1}, Lcom/longoipo/rc4/Rc4Bridge;->closeQuietly(Ljava/net/Socket;)V

    .line 268
    invoke-static {p2}, Lcom/longoipo/rc4/Rc4Bridge;->closeQuietly(Ljava/net/Socket;)V

    return-void
.end method

.method private pumpToServer(Ljava/net/Socket;Ljava/net/Socket;)V
    .registers 10

    .line 226
    :try_start_0
    invoke-virtual {p1}, Ljava/net/Socket;->getInputStream()Ljava/io/InputStream;

    move-result-object v0

    .line 227
    invoke-virtual {p2}, Ljava/net/Socket;->getOutputStream()Ljava/io/OutputStream;

    move-result-object v1

    const/16 v2, 0x10

    .line 228
    new-array v3, v2, [B

    .line 229
    sget-object v4, Lcom/longoipo/rc4/Rc4Bridge;->RANDOM:Ljava/security/SecureRandom;

    invoke-virtual {v4, v3}, Ljava/security/SecureRandom;->nextBytes([B)V

    .line 230
    iget-object p0, p0, Lcom/longoipo/rc4/Rc4Bridge;->masterKey:[B

    const/4 v4, 0x0

    invoke-static {p0, v3, v4}, Lcom/longoipo/rc4/Rc4Md5;->stream([B[BI)Lcom/longoipo/rc4/Rc4Md5$Rc4;

    move-result-object p0

    const/16 v5, 0x4010

    .line 231
    new-array v5, v5, [B

    .line 232
    invoke-static {v3, v4, v5, v4, v2}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V

    const/4 v3, 0x1

    :goto_20
    const/16 v6, 0x4000

    .line 235
    invoke-virtual {v0, v5, v2, v6}, Ljava/io/InputStream;->read([BII)I

    move-result v6

    if-ltz v6, :cond_38

    .line 236
    invoke-virtual {p0, v5, v2, v6}, Lcom/longoipo/rc4/Rc4Md5$Rc4;->crypt([BII)V

    if-eqz v3, :cond_34

    add-int/lit8 v6, v6, 0x10

    .line 238
    invoke-virtual {v1, v5, v4, v6}, Ljava/io/OutputStream;->write([BII)V

    move v3, v4

    goto :goto_20

    .line 241
    :cond_34
    invoke-virtual {v1, v5, v2, v6}, Ljava/io/OutputStream;->write([BII)V

    goto :goto_20

    .line 244
    :cond_38
    invoke-virtual {p2}, Ljava/net/Socket;->shutdownOutput()V
    :try_end_3b
    .catch Ljava/io/IOException; {:try_start_0 .. :try_end_3b} :catch_3c

    return-void

    .line 246
    :catch_3c
    invoke-static {p1}, Lcom/longoipo/rc4/Rc4Bridge;->closeQuietly(Ljava/net/Socket;)V

    .line 247
    invoke-static {p2}, Lcom/longoipo/rc4/Rc4Bridge;->closeQuietly(Ljava/net/Socket;)V

    return-void
.end method

.method private static readFully(Ljava/io/InputStream;[B)Z
    .registers 5
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    const/4 v0, 0x0

    move v1, v0

    .line 274
    :goto_2
    array-length v2, p1

    if-ge v1, v2, :cond_10

    .line 275
    array-length v2, p1

    sub-int/2addr v2, v1

    invoke-virtual {p0, p1, v1, v2}, Ljava/io/InputStream;->read([BII)I

    move-result v2

    if-gez v2, :cond_e

    return v0

    :cond_e
    add-int/2addr v1, v2

    goto :goto_2

    :cond_10
    const/4 p0, 0x1

    return p0
.end method

.method public static rewrite(Ljava/lang/String;)Ljava/lang/String;
    .registers 18

    move-object/from16 v1, p0

    .line 71
    const-string v0, "port"

    const-string v2, "address"

    const-string v3, "method"

    if-eqz v1, :cond_f7

    sget-object v4, Ljava/util/Locale;->ROOT:Ljava/util/Locale;

    invoke-virtual {v1, v4}, Ljava/lang/String;->toLowerCase(Ljava/util/Locale;)Ljava/lang/String;

    move-result-object v4

    const-string v5, "rc4-md5"

    invoke-virtual {v4, v5}, Ljava/lang/String;->indexOf(Ljava/lang/String;)I

    move-result v4

    if-gez v4, :cond_1a

    goto/16 :goto_f7

    .line 73
    :cond_1a
    :try_start_1a
    new-instance v4, Lorg/json/JSONObject;

    invoke-direct {v4, v1}, Lorg/json/JSONObject;-><init>(Ljava/lang/String;)V

    .line 74
    const-string v6, "outbounds"

    invoke-virtual {v4, v6}, Lorg/json/JSONObject;->optJSONArray(Ljava/lang/String;)Lorg/json/JSONArray;

    move-result-object v6

    if-nez v6, :cond_29

    goto/16 :goto_f7

    :cond_29
    const/4 v8, 0x0

    const/4 v9, 0x0

    .line 77
    :goto_2b
    invoke-virtual {v6}, Lorg/json/JSONArray;->length()I

    move-result v10

    if-ge v8, v10, :cond_dd

    .line 78
    invoke-virtual {v6, v8}, Lorg/json/JSONArray;->optJSONObject(I)Lorg/json/JSONObject;

    move-result-object v10

    if-eqz v10, :cond_d9

    .line 79
    const-string v11, "shadowsocks"

    const-string v12, "protocol"

    invoke-virtual {v10, v12}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v12

    invoke-virtual {v11, v12}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z

    move-result v11

    if-nez v11, :cond_47

    goto/16 :goto_d9

    .line 80
    :cond_47
    const-string v11, "settings"

    invoke-virtual {v10, v11}, Lorg/json/JSONObject;->optJSONObject(Ljava/lang/String;)Lorg/json/JSONObject;

    move-result-object v11

    if-nez v11, :cond_51

    const/4 v11, 0x0

    goto :goto_57

    .line 81
    :cond_51
    const-string v12, "servers"

    invoke-virtual {v11, v12}, Lorg/json/JSONObject;->optJSONArray(Ljava/lang/String;)Lorg/json/JSONArray;

    move-result-object v11

    :goto_57
    if-nez v11, :cond_5b

    goto/16 :goto_d9

    :cond_5b
    const/4 v12, 0x0

    .line 83
    :goto_5c
    invoke-virtual {v11}, Lorg/json/JSONArray;->length()I

    move-result v13

    if-ge v12, v13, :cond_d9

    .line 84
    invoke-virtual {v11, v12}, Lorg/json/JSONArray;->optJSONObject(I)Lorg/json/JSONObject;

    move-result-object v13

    if-eqz v13, :cond_d6

    .line 85
    invoke-virtual {v13, v3}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v14

    invoke-virtual {v5, v14}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z

    move-result v14

    if-nez v14, :cond_73

    goto :goto_d6

    .line 86
    :cond_73
    const-string v14, "streamSettings"

    invoke-virtual {v10, v14}, Lorg/json/JSONObject;->optJSONObject(Ljava/lang/String;)Lorg/json/JSONObject;

    move-result-object v14

    invoke-static {v14}, Lcom/longoipo/rc4/Rc4Bridge;->isPlainTcp(Lorg/json/JSONObject;)Z

    move-result v14

    if-nez v14, :cond_a2

    .line 87
    new-instance v13, Ljava/lang/StringBuilder;

    invoke-direct {v13}, Ljava/lang/StringBuilder;-><init>()V

    const-string v14, "outbound \'"

    invoke-virtual {v13, v14}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v13

    const-string v14, "tag"

    invoke-virtual {v10, v14}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v14

    invoke-virtual {v13, v14}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v13

    const-string v14, "\' uses non-plain transport; left unchanged"

    invoke-virtual {v13, v14}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v13

    invoke-virtual {v13}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v13

    invoke-static {v13}, Lcom/longoipo/rc4/Rc4Bridge;->warn(Ljava/lang/String;)V

    goto :goto_d6

    .line 90
    :cond_a2
    invoke-virtual {v13, v2}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v14

    const/4 v15, -0x1

    .line 91
    invoke-virtual {v13, v0, v15}, Lorg/json/JSONObject;->optInt(Ljava/lang/String;I)I

    move-result v15

    .line 92
    invoke-virtual {v14}, Ljava/lang/String;->isEmpty()Z

    move-result v16

    if-nez v16, :cond_d6

    const/4 v7, 0x1

    if-lt v15, v7, :cond_d6

    const v7, 0xffff

    if-le v15, v7, :cond_ba

    goto :goto_d6

    .line 93
    :cond_ba
    const-string v7, "password"

    invoke-virtual {v13, v7}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v7

    invoke-static {v14, v15, v7}, Lcom/longoipo/rc4/Rc4Bridge;->open(Ljava/lang/String;ILjava/lang/String;)Lcom/longoipo/rc4/Rc4Bridge;

    move-result-object v7

    .line 94
    const-string v9, "127.0.0.1"

    invoke-virtual {v13, v2, v9}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;

    .line 95
    invoke-virtual {v7}, Lcom/longoipo/rc4/Rc4Bridge;->getPort()I

    move-result v7

    invoke-virtual {v13, v0, v7}, Lorg/json/JSONObject;->put(Ljava/lang/String;I)Lorg/json/JSONObject;

    .line 96
    const-string v7, "none"

    invoke-virtual {v13, v3, v7}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;

    const/4 v9, 0x1

    :cond_d6
    :goto_d6
    add-int/lit8 v12, v12, 0x1

    goto :goto_5c

    :cond_d9
    :goto_d9
    add-int/lit8 v8, v8, 0x1

    goto/16 :goto_2b

    :cond_dd
    if-eqz v9, :cond_f7

    .line 100
    invoke-virtual {v4}, Lorg/json/JSONObject;->toString()Ljava/lang/String;

    move-result-object v0
    :try_end_e3
    .catchall {:try_start_1a .. :try_end_e3} :catchall_e4

    return-object v0

    :catchall_e4
    move-exception v0

    .line 102
    new-instance v2, Ljava/lang/StringBuilder;

    const-string v3, "rewrite failed, config left unchanged: "

    invoke-direct {v2, v3}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v0}, Lcom/longoipo/rc4/Rc4Bridge;->warn(Ljava/lang/String;)V

    :cond_f7
    :goto_f7
    return-object v1
.end method

.method private start()V
    .registers 7

    const/4 v0, 0x4

    .line 148
    :try_start_1
    new-array v0, v0, [B

    fill-array-data v0, :array_bc

    invoke-static {v0}, Ljava/net/InetAddress;->getByAddress([B)Ljava/net/InetAddress;

    move-result-object v0

    const/4 v1, 0x0

    move v2, v1

    :goto_c
    const/16 v3, 0x14

    const/16 v4, 0x40

    if-ge v2, v3, :cond_3b

    .line 149
    iget-object v3, p0, Lcom/longoipo/rc4/Rc4Bridge;->udp:Ljava/net/DatagramSocket;

    if-nez v3, :cond_3b

    .line 150
    new-instance v3, Ljava/net/ServerSocket;

    invoke-direct {v3, v1, v4, v0}, Ljava/net/ServerSocket;-><init>(IILjava/net/InetAddress;)V

    iput-object v3, p0, Lcom/longoipo/rc4/Rc4Bridge;->tcp:Ljava/net/ServerSocket;
    :try_end_1d
    .catch Ljava/io/IOException; {:try_start_1 .. :try_end_1d} :catch_b3

    .line 152
    :try_start_1d
    new-instance v3, Ljava/net/DatagramSocket;

    new-instance v4, Ljava/net/InetSocketAddress;

    iget-object v5, p0, Lcom/longoipo/rc4/Rc4Bridge;->tcp:Ljava/net/ServerSocket;

    invoke-virtual {v5}, Ljava/net/ServerSocket;->getLocalPort()I

    move-result v5

    invoke-direct {v4, v0, v5}, Ljava/net/InetSocketAddress;-><init>(Ljava/net/InetAddress;I)V

    invoke-direct {v3, v4}, Ljava/net/DatagramSocket;-><init>(Ljava/net/SocketAddress;)V

    iput-object v3, p0, Lcom/longoipo/rc4/Rc4Bridge;->udp:Ljava/net/DatagramSocket;
    :try_end_2f
    .catch Ljava/net/SocketException; {:try_start_1d .. :try_end_2f} :catch_30
    .catch Ljava/io/IOException; {:try_start_1d .. :try_end_2f} :catch_b3

    goto :goto_38

    .line 154
    :catch_30
    :try_start_30
    iget-object v3, p0, Lcom/longoipo/rc4/Rc4Bridge;->tcp:Ljava/net/ServerSocket;

    invoke-virtual {v3}, Ljava/net/ServerSocket;->close()V

    const/4 v3, 0x0

    .line 155
    iput-object v3, p0, Lcom/longoipo/rc4/Rc4Bridge;->tcp:Ljava/net/ServerSocket;

    :goto_38
    add-int/lit8 v2, v2, 0x1

    goto :goto_c

    .line 158
    :cond_3b
    iget-object v2, p0, Lcom/longoipo/rc4/Rc4Bridge;->tcp:Ljava/net/ServerSocket;
    :try_end_3d
    .catch Ljava/io/IOException; {:try_start_30 .. :try_end_3d} :catch_b3

    const-string v3, ":"

    if-nez v2, :cond_6a

    .line 159
    :try_start_41
    new-instance v2, Ljava/net/ServerSocket;

    invoke-direct {v2, v1, v4, v0}, Ljava/net/ServerSocket;-><init>(IILjava/net/InetAddress;)V

    iput-object v2, p0, Lcom/longoipo/rc4/Rc4Bridge;->tcp:Ljava/net/ServerSocket;

    .line 160
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "no UDP port available next to TCP; UDP disabled for "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    iget-object v1, p0, Lcom/longoipo/rc4/Rc4Bridge;->host:Ljava/lang/String;

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    iget v1, p0, Lcom/longoipo/rc4/Rc4Bridge;->port:I

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v0}, Lcom/longoipo/rc4/Rc4Bridge;->warn(Ljava/lang/String;)V
    :try_end_6a
    .catch Ljava/io/IOException; {:try_start_41 .. :try_end_6a} :catch_b3

    .line 165
    :cond_6a
    sget-object v0, Lcom/longoipo/rc4/Rc4Bridge;->POOL:Ljava/util/concurrent/ExecutorService;

    new-instance v1, Lcom/longoipo/rc4/Rc4Bridge$2;

    invoke-direct {v1, p0}, Lcom/longoipo/rc4/Rc4Bridge$2;-><init>(Lcom/longoipo/rc4/Rc4Bridge;)V

    invoke-interface {v0, v1}, Ljava/util/concurrent/ExecutorService;->execute(Ljava/lang/Runnable;)V

    .line 171
    iget-object v1, p0, Lcom/longoipo/rc4/Rc4Bridge;->udp:Ljava/net/DatagramSocket;

    if-eqz v1, :cond_80

    .line 172
    new-instance v1, Lcom/longoipo/rc4/Rc4Bridge$3;

    invoke-direct {v1, p0}, Lcom/longoipo/rc4/Rc4Bridge$3;-><init>(Lcom/longoipo/rc4/Rc4Bridge;)V

    invoke-interface {v0, v1}, Ljava/util/concurrent/ExecutorService;->execute(Ljava/lang/Runnable;)V

    .line 179
    :cond_80
    new-instance v0, Ljava/lang/StringBuilder;

    const-string v1, "bridge 127.0.0.1:"

    invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {p0}, Lcom/longoipo/rc4/Rc4Bridge;->getPort()I

    move-result v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v0

    const-string v1, " -> "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    iget-object v1, p0, Lcom/longoipo/rc4/Rc4Bridge;->host:Ljava/lang/String;

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    iget p0, p0, Lcom/longoipo/rc4/Rc4Bridge;->port:I

    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string v0, " (rc4-md5)"

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    invoke-static {p0}, Lcom/longoipo/rc4/Rc4Bridge;->warn(Ljava/lang/String;)V

    return-void

    :catch_b3
    move-exception p0

    .line 163
    new-instance v0, Ljava/lang/IllegalStateException;

    const-string v1, "cannot bind loopback listener"

    invoke-direct {v0, v1, p0}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;Ljava/lang/Throwable;)V

    throw v0

    :array_bc
    .array-data 1
        0x7ft
        0x0t
        0x0t
        0x1t
    .end array-data
.end method

.method private udpLoop()V
    .registers 7

    const v0, 0xffff

    .line 285
    new-array v1, v0, [B

    .line 286
    new-instance v2, Ljava/net/DatagramPacket;

    invoke-direct {v2, v1, v0}, Ljava/net/DatagramPacket;-><init>([BI)V

    .line 287
    :cond_a
    :goto_a
    iget-object v3, p0, Lcom/longoipo/rc4/Rc4Bridge;->udp:Ljava/net/DatagramSocket;

    invoke-virtual {v3}, Ljava/net/DatagramSocket;->isClosed()Z

    move-result v3

    if-nez v3, :cond_6b

    .line 289
    :try_start_12
    invoke-virtual {v2, v0}, Ljava/net/DatagramPacket;->setLength(I)V

    .line 290
    iget-object v3, p0, Lcom/longoipo/rc4/Rc4Bridge;->udp:Ljava/net/DatagramSocket;

    invoke-virtual {v3, v2}, Ljava/net/DatagramSocket;->receive(Ljava/net/DatagramPacket;)V

    .line 291
    invoke-virtual {v2}, Ljava/net/DatagramPacket;->getSocketAddress()Ljava/net/SocketAddress;

    move-result-object v3

    .line 292
    iget-object v4, p0, Lcom/longoipo/rc4/Rc4Bridge;->flows:Ljava/util/concurrent/ConcurrentHashMap;

    invoke-virtual {v4, v3}, Ljava/util/concurrent/ConcurrentHashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Lcom/longoipo/rc4/Rc4Bridge$Flow;

    if-nez v4, :cond_47

    .line 294
    iget-object v4, p0, Lcom/longoipo/rc4/Rc4Bridge;->flows:Ljava/util/concurrent/ConcurrentHashMap;

    invoke-virtual {v4}, Ljava/util/concurrent/ConcurrentHashMap;->size()I

    move-result v4

    const/16 v5, 0x400

    if-lt v4, v5, :cond_33

    goto :goto_a

    .line 295
    :cond_33
    new-instance v4, Lcom/longoipo/rc4/Rc4Bridge$Flow;

    invoke-direct {v4, p0, v3}, Lcom/longoipo/rc4/Rc4Bridge$Flow;-><init>(Lcom/longoipo/rc4/Rc4Bridge;Ljava/net/SocketAddress;)V

    .line 296
    iget-object v5, p0, Lcom/longoipo/rc4/Rc4Bridge;->flows:Ljava/util/concurrent/ConcurrentHashMap;

    invoke-virtual {v5, v3, v4}, Ljava/util/concurrent/ConcurrentHashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    .line 298
    sget-object v3, Lcom/longoipo/rc4/Rc4Bridge;->POOL:Ljava/util/concurrent/ExecutorService;

    new-instance v5, Lcom/longoipo/rc4/Rc4Bridge$6;

    invoke-direct {v5, p0, v4}, Lcom/longoipo/rc4/Rc4Bridge$6;-><init>(Lcom/longoipo/rc4/Rc4Bridge;Lcom/longoipo/rc4/Rc4Bridge$Flow;)V

    invoke-interface {v3, v5}, Ljava/util/concurrent/ExecutorService;->execute(Ljava/lang/Runnable;)V

    .line 305
    :cond_47
    invoke-virtual {v2}, Ljava/net/DatagramPacket;->getLength()I

    move-result v3

    invoke-virtual {v4, v1, v3}, Lcom/longoipo/rc4/Rc4Bridge$Flow;->sendToServer([BI)V
    :try_end_4e
    .catch Ljava/io/IOException; {:try_start_12 .. :try_end_4e} :catch_4f

    goto :goto_a

    :catch_4f
    move-exception v3

    .line 307
    iget-object v4, p0, Lcom/longoipo/rc4/Rc4Bridge;->udp:Ljava/net/DatagramSocket;

    invoke-virtual {v4}, Ljava/net/DatagramSocket;->isClosed()Z

    move-result v4

    if-nez v4, :cond_a

    new-instance v4, Ljava/lang/StringBuilder;

    const-string v5, "udp receive failed: "

    invoke-direct {v4, v5}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v4, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v3}, Lcom/longoipo/rc4/Rc4Bridge;->warn(Ljava/lang/String;)V

    goto :goto_a

    :cond_6b
    return-void
.end method

.method private static warn(Ljava/lang/String;)V
    .registers 4

    .line 373
    sget-object v0, Ljava/lang/System;->err:Ljava/io/PrintStream;

    new-instance v1, Ljava/lang/StringBuilder;

    const-string v2, "[Rc4Bridge] "

    invoke-direct {v1, v2}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v1, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    invoke-virtual {v0, p0}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V

    return-void
.end method


# virtual methods
.method public getPort()I
    .registers 1

    .line 139
    iget-object p0, p0, Lcom/longoipo/rc4/Rc4Bridge;->tcp:Ljava/net/ServerSocket;

    invoke-virtual {p0}, Ljava/net/ServerSocket;->getLocalPort()I

    move-result p0

    return p0
.end method
