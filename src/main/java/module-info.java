/** JVM module specification for Cache Utils lib */
module io.github.wcarmon.config {
    requires org.jetbrains.annotations;

    // TODO: remove this
    requires static lombok;

    exports io.github.wcarmon.cache;
}
