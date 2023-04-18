package zio.temporal.internal

import zio.Duration
import scala.reflect.macros.blackbox
import zio.temporal.ZWorkflowExecution
import zio.temporal.workflow.ZWorkflowStub

class ZWorkflowMacro(override val c: blackbox.Context) extends InvocationMacroUtils(c) {
  import c.universe._

  private val ZWorkflowExecution = typeOf[ZWorkflowExecution]
  private val zworkflowStub      = typeOf[ZWorkflowStub.type].dealias

  def startImpl[A: WeakTypeTag](f: Expr[A]): Tree = {
    // Assert called on ZWorkflowStub
    assertPrefixType(zworkflowStub)

    val invocation = getMethodInvocation(f.tree)
    assertWorkflow(invocation.instance.tpe)

    val method = invocation.getMethod(SharedCompileTimeMessages.wfMethodShouldntBeExtMethod)
    method.assertWorkflowMethod()

    val startInvocation = workflowStartInvocation(invocation, method)

    q"""
      _root_.zio.temporal.internal.TemporalInteraction.from {
        new $ZWorkflowExecution(
          $startInvocation
        )
      } 
     """.debugged(SharedCompileTimeMessages.generatedWorkflowStart)
  }

  def executeImpl[R: WeakTypeTag](f: Expr[R]): Tree = {
    // Assert called on ZWorkflowStub
    assertPrefixType(zworkflowStub)

    val invocation = getMethodInvocation(f.tree)
    assertWorkflow(invocation.instance.tpe)

    val method = invocation.getMethod(SharedCompileTimeMessages.wfMethodShouldntBeExtMethod)
    method.assertWorkflowMethod()

    val executeInvocation = workflowExecuteInvocation(invocation, method, weakTypeOf[R])

    q"""
      _root_.zio.temporal.internal.TemporalInteraction.fromFuture {
        $executeInvocation
      } 
     """.debugged(SharedCompileTimeMessages.generatedWorkflowExecute)
  }

  def executeWithTimeoutImpl[R: WeakTypeTag](timeout: Expr[Duration])(f: Expr[R]): Tree = {
    // Assert called on ZWorkflowStub
    assertPrefixType(zworkflowStub)

    val invocation = getMethodInvocation(f.tree)
    assertWorkflow(invocation.instance.tpe)

    val method = invocation.getMethod(SharedCompileTimeMessages.wfMethodShouldntBeExtMethod)
    method.assertWorkflowMethod()

    val executeInvocation = workflowExecuteWithTimeoutInvocation(invocation, method, timeout, weakTypeOf[R])

    q"""
      _root_.zio.temporal.internal.TemporalInteraction.fromFuture {
        $executeInvocation
      } 
     """.debugged(SharedCompileTimeMessages.generatedWorkflowExecute)
  }

  private def workflowStartInvocation(
    invocation: MethodInvocation,
    method:     MethodInfo
  ): Tree = {
    val stub = q"""${invocation.instance}.toJava"""
    q"""_root_.zio.temporal.internal.TemporalWorkflowFacade.start($stub, List(..${method.appliedArgs}))"""
  }

  private def workflowExecuteInvocation(
    invocation: MethodInvocation,
    method:     MethodInfo,
    ret:        Type
  ): Tree = {
    val stub = q"""${invocation.instance}.toJava"""
    q"""_root_.zio.temporal.internal.TemporalWorkflowFacade.execute[$ret]($stub, List(..${method.appliedArgs}))"""
  }

  private def workflowExecuteWithTimeoutInvocation(
    invocation: MethodInvocation,
    method:     MethodInfo,
    timeout:    Expr[Duration],
    ret:        Type
  ): Tree = {
    val stub = q"""${invocation.instance}.toJava"""
    q"""_root_.zio.temporal.internal.TemporalWorkflowFacade.executeWithTimeout[$ret]($stub, $timeout, List(..${method.appliedArgs}))"""
  }
}
