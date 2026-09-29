.class final Lcom/longoipo/rc4/Rc4Md5$Rc4;
.super Ljava/lang/Object;
.source "Rc4Md5.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/longoipo/rc4/Rc4Md5;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x18
    name = "Rc4"
.end annotation


# instance fields
.field private i:I

.field private j:I

.field private final s:[I


# direct methods
.method constructor <init>([B)V
    .registers 8

    .line 51
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    const/16 v0, 0x100

    .line 47
    new-array v1, v0, [I

    iput-object v1, p0, Lcom/longoipo/rc4/Rc4Md5$Rc4;->s:[I

    const/4 v1, 0x0

    move v2, v1

    :goto_b
    if-ge v2, v0, :cond_14

    .line 52
    iget-object v3, p0, Lcom/longoipo/rc4/Rc4Md5$Rc4;->s:[I

    aput v2, v3, v2

    add-int/lit8 v2, v2, 0x1

    goto :goto_b

    :cond_14
    move v2, v1

    :goto_15
    if-ge v1, v0, :cond_2f

    .line 55
    iget-object v3, p0, Lcom/longoipo/rc4/Rc4Md5$Rc4;->s:[I

    aget v4, v3, v1

    add-int/2addr v2, v4

    array-length v5, p1

    rem-int v5, v1, v5

    aget-byte v5, p1, v5

    and-int/lit16 v5, v5, 0xff

    add-int/2addr v2, v5

    and-int/lit16 v2, v2, 0xff

    .line 57
    aget v5, v3, v2

    aput v5, v3, v1

    .line 58
    aput v4, v3, v2

    add-int/lit8 v1, v1, 0x1

    goto :goto_15

    :cond_2f
    return-void
.end method


# virtual methods
.method crypt([BII)V
    .registers 12

    .line 64
    iget v0, p0, Lcom/longoipo/rc4/Rc4Md5$Rc4;->i:I

    .line 65
    iget v1, p0, Lcom/longoipo/rc4/Rc4Md5$Rc4;->j:I

    const/4 v2, 0x0

    :goto_5
    if-ge v2, p3, :cond_2b

    add-int/lit8 v0, v0, 0x1

    and-int/lit16 v0, v0, 0xff

    .line 68
    iget-object v3, p0, Lcom/longoipo/rc4/Rc4Md5$Rc4;->s:[I

    aget v4, v3, v0

    add-int/2addr v1, v4

    and-int/lit16 v1, v1, 0xff

    .line 70
    aget v5, v3, v1

    aput v5, v3, v0

    .line 71
    aput v4, v3, v1

    add-int v5, p2, v2

    .line 72
    aget-byte v6, p1, v5

    aget v7, v3, v0

    add-int/2addr v7, v4

    and-int/lit16 v4, v7, 0xff

    aget v3, v3, v4

    int-to-byte v3, v3

    xor-int/2addr v3, v6

    int-to-byte v3, v3

    aput-byte v3, p1, v5

    add-int/lit8 v2, v2, 0x1

    goto :goto_5

    .line 74
    :cond_2b
    iput v0, p0, Lcom/longoipo/rc4/Rc4Md5$Rc4;->i:I

    .line 75
    iput v1, p0, Lcom/longoipo/rc4/Rc4Md5$Rc4;->j:I

    return-void
.end method
