.class Lcom/longoipo/rc4/Rc4Bridge$6;
.super Ljava/lang/Object;
.source "Rc4Bridge.java"

# interfaces
.implements Ljava/lang/Runnable;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/longoipo/rc4/Rc4Bridge;->udpLoop()V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$0:Lcom/longoipo/rc4/Rc4Bridge;

.field final synthetic val$nf:Lcom/longoipo/rc4/Rc4Bridge$Flow;


# direct methods
.method constructor <init>(Lcom/longoipo/rc4/Rc4Bridge;Lcom/longoipo/rc4/Rc4Bridge$Flow;)V
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

    .line 298
    iput-object p1, p0, Lcom/longoipo/rc4/Rc4Bridge$6;->this$0:Lcom/longoipo/rc4/Rc4Bridge;

    iput-object p2, p0, Lcom/longoipo/rc4/Rc4Bridge$6;->val$nf:Lcom/longoipo/rc4/Rc4Bridge$Flow;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public run()V
    .registers 1

    .line 301
    iget-object p0, p0, Lcom/longoipo/rc4/Rc4Bridge$6;->val$nf:Lcom/longoipo/rc4/Rc4Bridge$Flow;

    invoke-virtual {p0}, Lcom/longoipo/rc4/Rc4Bridge$Flow;->readLoop()V

    return-void
.end method
