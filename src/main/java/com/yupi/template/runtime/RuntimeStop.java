package com.yupi.template.runtime;
/** Expected durable stop, never a model review issue. */
public class RuntimeStop extends RuntimeException {
 public final String status;
 public RuntimeStop(String status) { super(status); this.status=status; }
}
