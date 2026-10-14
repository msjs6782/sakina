// Local-only stand-in so tools/test_core_locally.sh can compile @Serializable core models without the
// kotlinx.serialization library. Gradle builds use the real annotation and compiler plugin.
package kotlinx.serialization

@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.TYPE)
annotation class Serializable
