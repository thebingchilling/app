.class Lcom/longoipo/rc4/Rc4Bridge$2;
.super Ljava/lang/Object;
.source "Rc4Bridge.java"

# interfaces
.implements Ljava/lang/Runnable;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/longoipo/rc4/Rc4Bridge;->start()V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$0:Lcom/longoipo/rc4/Rc4Bridge;


# direct methods
.method constructor <init>(Lcom/longoipo/rc4/Rc4Bridge;)V
    .registers 2
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x8010
        }
        names = {
            null
        }
    .end annotation

    .line 165
    iput-object p1, p0, Lcom/longoipo/rc4/Rc4Bridge$2;->this$0:Lcom/longoipo/rc4/Rc4Bridge;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public run()V
    .registers 1

    .line 168
    iget-object p0, p0, Lcom/longoipo/rc4/Rc4Bridge$2;->this$0:Lcom/longoipo/rc4/Rc4Bridge;

    # invokes: Lcom/longoipo/rc4/Rc4Bridge;->acceptLoop()V
    invoke-static {p0}, Lcom/longoipo/rc4/Rc4Bridge;->access$000(Lcom/longoipo/rc4/Rc4Bridge;)V

    return-void
.end method
