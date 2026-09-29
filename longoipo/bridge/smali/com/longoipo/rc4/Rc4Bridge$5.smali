.class Lcom/longoipo/rc4/Rc4Bridge$5;
.super Ljava/lang/Object;
.source "Rc4Bridge.java"

# interfaces
.implements Ljava/lang/Runnable;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/longoipo/rc4/Rc4Bridge;->handleTcp(Ljava/net/Socket;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$0:Lcom/longoipo/rc4/Rc4Bridge;

.field final synthetic val$c:Ljava/net/Socket;

.field final synthetic val$fu:Ljava/net/Socket;


# direct methods
.method constructor <init>(Lcom/longoipo/rc4/Rc4Bridge;Ljava/net/Socket;Ljava/net/Socket;)V
    .registers 4
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x8010,
            0x1010,
            0x1010
        }
        names = {
            null,
            null,
            null
        }
    .end annotation

    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()V"
        }
    .end annotation

    .line 207
    iput-object p1, p0, Lcom/longoipo/rc4/Rc4Bridge$5;->this$0:Lcom/longoipo/rc4/Rc4Bridge;

    iput-object p2, p0, Lcom/longoipo/rc4/Rc4Bridge$5;->val$c:Ljava/net/Socket;

    iput-object p3, p0, Lcom/longoipo/rc4/Rc4Bridge$5;->val$fu:Ljava/net/Socket;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public run()V
    .registers 3

    .line 210
    iget-object v0, p0, Lcom/longoipo/rc4/Rc4Bridge$5;->this$0:Lcom/longoipo/rc4/Rc4Bridge;

    iget-object v1, p0, Lcom/longoipo/rc4/Rc4Bridge$5;->val$c:Ljava/net/Socket;

    iget-object p0, p0, Lcom/longoipo/rc4/Rc4Bridge$5;->val$fu:Ljava/net/Socket;

    # invokes: Lcom/longoipo/rc4/Rc4Bridge;->pumpToServer(Ljava/net/Socket;Ljava/net/Socket;)V
    invoke-static {v0, v1, p0}, Lcom/longoipo/rc4/Rc4Bridge;->access$300(Lcom/longoipo/rc4/Rc4Bridge;Ljava/net/Socket;Ljava/net/Socket;)V

    return-void
.end method
