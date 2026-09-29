.class Lcom/longoipo/rc4/Rc4Bridge$4;
.super Ljava/lang/Object;
.source "Rc4Bridge.java"

# interfaces
.implements Ljava/lang/Runnable;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/longoipo/rc4/Rc4Bridge;->acceptLoop()V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$0:Lcom/longoipo/rc4/Rc4Bridge;

.field final synthetic val$c:Ljava/net/Socket;


# direct methods
.method constructor <init>(Lcom/longoipo/rc4/Rc4Bridge;Ljava/net/Socket;)V
    .registers 3
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x8010,
            0x1010
        }
        names = {
            null,
            null
        }
    .end annotation

    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()V"
        }
    .end annotation

    .line 188
    iput-object p1, p0, Lcom/longoipo/rc4/Rc4Bridge$4;->this$0:Lcom/longoipo/rc4/Rc4Bridge;

    iput-object p2, p0, Lcom/longoipo/rc4/Rc4Bridge$4;->val$c:Ljava/net/Socket;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public run()V
    .registers 2

    .line 191
    iget-object v0, p0, Lcom/longoipo/rc4/Rc4Bridge$4;->this$0:Lcom/longoipo/rc4/Rc4Bridge;

    iget-object p0, p0, Lcom/longoipo/rc4/Rc4Bridge$4;->val$c:Ljava/net/Socket;

    # invokes: Lcom/longoipo/rc4/Rc4Bridge;->handleTcp(Ljava/net/Socket;)V
    invoke-static {v0, p0}, Lcom/longoipo/rc4/Rc4Bridge;->access$200(Lcom/longoipo/rc4/Rc4Bridge;Ljava/net/Socket;)V

    return-void
.end method
