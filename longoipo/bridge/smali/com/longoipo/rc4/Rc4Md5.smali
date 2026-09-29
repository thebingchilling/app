.class final Lcom/longoipo/rc4/Rc4Md5;
.super Ljava/lang/Object;
.source "Rc4Md5.java"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/longoipo/rc4/Rc4Md5$Rc4;
    }
.end annotation


# static fields
.field static final IV_LEN:I = 0x10

.field static final KEY_LEN:I = 0x10


# direct methods
.method private constructor <init>()V
    .registers 1

    .line 10
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method static deriveKey([B)[B
    .registers 9

    .line 15
    :try_start_0
    const-string v0, "MD5"

    invoke-static {v0}, Ljava/security/MessageDigest;->getInstance(Ljava/lang/String;)Ljava/security/MessageDigest;

    move-result-object v0

    const/16 v1, 0x10

    .line 16
    new-array v2, v1, [B

    const/4 v3, 0x0

    .line 17
    new-array v4, v3, [B

    move v5, v3

    :goto_e
    if-ge v5, v1, :cond_29

    .line 20
    invoke-virtual {v0}, Ljava/security/MessageDigest;->reset()V

    .line 21
    invoke-virtual {v0, v4}, Ljava/security/MessageDigest;->update([B)V

    .line 22
    invoke-virtual {v0, p0}, Ljava/security/MessageDigest;->update([B)V

    .line 23
    invoke-virtual {v0}, Ljava/security/MessageDigest;->digest()[B

    move-result-object v4

    .line 24
    array-length v6, v4

    rsub-int/lit8 v7, v5, 0x10

    invoke-static {v6, v7}, Ljava/lang/Math;->min(II)I

    move-result v6

    .line 25
    invoke-static {v4, v3, v2, v5, v6}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V
    :try_end_27
    .catch Ljava/security/NoSuchAlgorithmException; {:try_start_0 .. :try_end_27} :catch_2a

    add-int/2addr v5, v6

    goto :goto_e

    :cond_29
    return-object v2

    :catch_2a
    move-exception p0

    .line 30
    new-instance v0, Ljava/lang/IllegalStateException;

    invoke-direct {v0, p0}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/Throwable;)V

    throw v0
.end method

.method static stream([B[BI)Lcom/longoipo/rc4/Rc4Md5$Rc4;
    .registers 4

    .line 37
    :try_start_0
    const-string v0, "MD5"

    invoke-static {v0}, Ljava/security/MessageDigest;->getInstance(Ljava/lang/String;)Ljava/security/MessageDigest;

    move-result-object v0

    .line 38
    invoke-virtual {v0, p0}, Ljava/security/MessageDigest;->update([B)V

    const/16 p0, 0x10

    .line 39
    invoke-virtual {v0, p1, p2, p0}, Ljava/security/MessageDigest;->update([BII)V

    .line 40
    new-instance p0, Lcom/longoipo/rc4/Rc4Md5$Rc4;

    invoke-virtual {v0}, Ljava/security/MessageDigest;->digest()[B

    move-result-object p1

    invoke-direct {p0, p1}, Lcom/longoipo/rc4/Rc4Md5$Rc4;-><init>([B)V
    :try_end_17
    .catch Ljava/security/NoSuchAlgorithmException; {:try_start_0 .. :try_end_17} :catch_18

    return-object p0

    :catch_18
    move-exception p0

    .line 42
    new-instance p1, Ljava/lang/IllegalStateException;

    invoke-direct {p1, p0}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/Throwable;)V

    throw p1
.end method
