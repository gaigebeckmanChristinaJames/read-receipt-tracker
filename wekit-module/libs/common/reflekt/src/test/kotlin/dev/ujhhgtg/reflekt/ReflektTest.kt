@file:Suppress("unused", "USELESS_IS_CHECK")

package dev.ujhhgtg.reflekt

import dev.ujhhgtg.reflekt.spec.AnyType
import dev.ujhhgtg.reflekt.utils.Modifiers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.Modifier

class ReflektTest {

    // ========== Fixtures ==========

    class FieldSubject {
        @JvmField
        var message: String = "initial"
    }

    open class MethodRoot {
        fun rootMethod() = "root"
    }

    open class MethodParent : MethodRoot() {
        fun parentMethod() = "parent"
    }

    class MethodChild : MethodParent() {
        fun childMethod() = "child"
    }

    open class FieldRoot {
        @JvmField
        val rootProp: String = "root"
    }

    open class FieldParent : FieldRoot() {
        @JvmField
        val parentProp: String = "parent"
    }

    class FieldChild : FieldParent() {
        @JvmField
        val childProp: String = "child"
    }

    // ========== Basic method lookup ==========

    @Test
    fun basicMethodLookup() {
        val method = "hello".reflekt().firstMethod { name = "length"; parameterCount = 0 }
        assertEquals(5, method.invoke())
        assertEquals("length", method.name)
        assertEquals(Int::class.java, method.returnType)
    }

    // ========== Field get/set ==========

    @Test
    fun fieldGetSet() {
        val obj = FieldSubject()
        val field = FieldSubject::class.reflekt().firstField { name = "message" }
        assertEquals("initial", field.get(obj))
        field.set(obj, "updated")
        assertEquals("updated", field.get(obj))
        assertEquals("updated", obj.message)
    }

    // ========== Constructor ==========

    @Test
    fun constructorNoArg() {
        val ctor = StringBuilder::class.reflekt().firstConstructor { parameterCount = 0 }
        val sb = ctor.newInstance()
        assertNotNull(sb)
        assertTrue(sb is StringBuilder)
    }

    // ========== Lambda condition (no caching) ==========

    @Test
    fun lambdaCondition() {
        val methods = String::class.reflekt().methods {
            name { it.startsWith("get") }
        }
        assertTrue(methods.isNotEmpty())
        assertTrue(methods.all { it.name.startsWith("get") })
    }

    // ========== Superclass method lookup ==========

    @Test
    fun superclassMethod() {
        val method = MethodChild::class.reflekt().firstMethod {
            name = "parentMethod"
            superclass = true
        }
        assertNotNull(method)
        assertEquals("parentMethod", method.name)

        val noMatch = MethodChild::class.reflekt().firstMethodOrNull {
            name = "parentMethod"
            superclass = false
        }
        assertNull(noMatch)
    }

    @Test
    fun superclassGrandparentMethod() {
        val method = MethodChild::class.reflekt().firstMethod {
            name = "rootMethod"
            superclass = true
        }
        assertNotNull(method)
        assertEquals("rootMethod", method.name)
    }

    // ========== No match: throws ==========

    @Test
    fun noMatchThrows() {
        assertThrows<NoSuchElementException> {
            String::class.reflekt().firstMethod { name = "nonexistentMethod" }
        }
    }

    @Test
    fun noMatchThrowsField() {
        assertThrows<NoSuchElementException> {
            String::class.reflekt().firstField { name = "nonexistentField" }
        }
    }

    @Test
    fun noMatchThrowsConstructor() {
        assertThrows<NoSuchElementException> {
            // String has no zero-arg constructor
            String::class.reflekt().firstConstructor { parameterCount = 99 }
        }
    }

    // ========== No match: returns null ==========

    @Test
    fun noMatchReturnsNull() {
        assertNull(String::class.reflekt().firstMethodOrNull { name = "nonexistentMethod" })
        assertNull(String::class.reflekt().firstFieldOrNull { name = "nonexistentField" })
    }

    // ========== Empty list ==========

    @Test
    fun emptyListOnNoMatch() {
        assertTrue(String::class.reflekt().methods { name = "nonexistentMethod" }.isEmpty())
        assertTrue(String::class.reflekt().fields { name = "nonexistentField" }.isEmpty())
    }

    // ========== Cache hit ==========

    @Test
    fun cacheHit() {
        ReflectionCache.clear()
        val m1 = String::class.reflekt().firstMethod { name = "length"; parameterCount = 0 }
        val m2 = String::class.reflekt().firstMethod { name = "length"; parameterCount = 0 }
        assertSame(m1, m2)
    }

    @Test
    fun cacheHitList() {
        ReflectionCache.clear()
        val list1 = String::class.reflekt().methods { name = "length" }
        val list2 = String::class.reflekt().methods { name = "length" }
        assertSame(list1, list2)
    }

    @Test
    fun cacheMissOnLambdaCondition() {
        ReflectionCache.clear()
        val m1 = String::class.reflekt().firstMethod { name { it == "length" } }
        val m2 = String::class.reflekt().firstMethod { name { it == "length" } }
        // Lambda → not cached → different wrapper instances; underlying methods same
        assertNotNull(m1)
        assertNotNull(m2)
        assertEquals(m1.self, m2.self)
    }

    // ========== Instance entry (reflekt on instance) ==========

    @Test
    fun instanceMethod() {
        val method = "hello".reflekt().firstMethod { name = "length" }
        assertEquals(5, method.invoke())
    }

    @Test
    fun instanceFieldGetSet() {
        val obj = FieldSubject()
        obj.message = "hello"
        val field = obj.reflekt().firstField { name = "message" }
        assertEquals("hello", field.get())
        field.set("world")
        assertEquals("world", field.get())
        assertEquals("world", obj.message)
    }

    @Test
    fun instanceConstructor() {
        val sb = StringBuilder("test")
        val ctors = sb.reflekt().constructors { parameterCount = 1 }
        assertTrue(ctors.isNotEmpty())
        // Instance-aware constructor just wraps metadata; newInstance still creates new instances
        val newSb = ctors.first().newInstance("new")
        assertEquals("new", newSb.toString())
    }

    // ========== Modifiers ==========

    @Test
    fun modifiersFilter() {
        val publicMethods = Math::class.reflekt().methods {
            modifiers { it.contains(Modifiers.PUBLIC) }
        }
        assertTrue(publicMethods.isNotEmpty())
        assertTrue(publicMethods.all { Modifier.isPublic(it.modifiers) })
    }

    @Test
    fun modifiersExactMatch() {
        val flags = Modifier.PUBLIC or Modifier.STATIC
        val staticMethods = Math::class.reflekt().methods { modifiers = flags }
        assertTrue(staticMethods.isNotEmpty())
        assertTrue(staticMethods.all { it.modifiers and flags == flags })
    }

    // ========== Superclass field ==========

    @Test
    fun superclassField() {
        val child = FieldChild()
        val field = FieldChild::class.reflekt().firstField {
            name = "parentProp"
            superclass = true
        }
        assertNotNull(field)
        assertEquals("parent", field.get(child))

        val noMatch = FieldChild::class.reflekt().firstFieldOrNull {
            name = "parentProp"
            superclass = false
        }
        assertNull(noMatch)
    }

    // ========== Type matching (primitive ↔ boxed) ==========

    @Test
    fun typeMatchesPrimitiveVsBoxed() {
        // String.length() returns int (primitive). Filtering for returnType = Int::class should match.
        val method = String::class.reflekt().firstMethod {
            name = "length"
            returnType = Int::class.java
        }
        assertNotNull(method)
        assertEquals(Int::class.java, method.returnType)
    }

    @Test
    fun constructorWithParameters() {
        // StringBuilder(capacity: Int) — parameter is int (primitive)
        val ctor = StringBuilder::class.reflekt().firstConstructor {
            parameterCount = 1
            parameters(Int::class)
        }
        assertNotNull(ctor)
        val sb = ctor.newInstance(100)
        assertEquals(100, sb.capacity())
    }

    // ========== KClass entry point ==========

    @Test
    fun kclassReflekt() {
        val method = "hello".reflekt().firstMethod { name = "length" }
        assertNotNull(method)
        assertEquals(5, method.invoke())
    }

    // ========== KClass property setter syntax ==========

    @Test
    fun returnTypeWithKClassProperty() {
        val method = String::class.reflekt().firstMethod {
            name = "length"
            returnType = Int::class   // KClass, not .java
        }
        assertNotNull(method)
        assertEquals(Int::class.java, method.returnType)
        assertEquals(5, "hello".reflekt().firstMethod { name = "length" }.invoke())
    }

    @Test
    fun fieldTypeWithKClassProperty() {
        val obj = FieldSubject()
        val field = FieldSubject::class.reflekt().firstField {
            type = String::class   // KClass, not .java
            name = "message"
        }
        assertEquals("initial", field.get(obj))
    }

    @Test
    fun constructorParametersWithKClassListProperty() {
        val ctor = StringBuilder::class.reflekt().firstConstructor {
            parameters = listOf(Int::class)   // KClass list, not .java
        }
        assertNotNull(ctor)
        val sb = ctor.newInstance(100)
        assertEquals(100, sb.capacity())
    }

    @Test
    fun cacheConsistencyAcrossClassAndKClass() {
        ReflectionCache.clear()
        val m1 = String::class.reflekt().firstMethod {
            name = "length"; returnType = Int::class
        }
        val m2 = String::class.reflekt().firstMethod {
            name = "length"; returnType = Int::class.java
        }
        assertSame(m1, m2)
    }

    // ========== AnyType in parameters ==========

    @Test
    fun anyTypeInParameters() {
        // String.startsWith(String, int) has 2 params: (String, int)
        val methods = String::class.reflekt().methods {
            parameters(AnyType, Int::class)
        }
        assertTrue(methods.isNotEmpty())
        assertTrue(methods.all { it.parameterTypes.size == 2 })
    }

    @Test
    fun anyTypeParameterCountPreserved() {
        // parameters(AnyType) requires exactly 1 param, not 0
        val zeroParam = String::class.reflekt().firstMethodOrNull {
            parameters(AnyType)
            name = "length"  // length() takes 0 params → no match
        }
        assertNull(zeroParam)
    }

    @Test
    fun anyTypeWithMultiplePositions() {
        // String.indexOf(String, int) has 2 params
        val method = String::class.reflekt().firstMethodOrNull {
            parameterCount = 2
            parameters(AnyType, AnyType)
        }
        assertNotNull(method)
    }

    // ========== Modifiers vararg & predicate ==========

    @Test
    fun modifiersVararg() {
        val staticMethods = Math::class.reflekt().methods {
            modifiers(Modifiers.PUBLIC, Modifiers.STATIC)
        }
        assertTrue(staticMethods.isNotEmpty())
        assertTrue(staticMethods.all { Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers) })
    }

    @Test
    fun modifiersPredicateWithSet() {
        val nonPrivateMethods = String::class.reflekt().methods {
            modifiers { !it.contains(Modifiers.PRIVATE) }
        }
        assertTrue(nonPrivateMethods.isNotEmpty())
        assertTrue(nonPrivateMethods.all { !Modifier.isPrivate(it.modifiers) })
    }

    // ========== Last method/field/constructor ==========

    @Test
    fun lastMethod() {
        // String has multiple methods named "valueOf" — last declared one
        val method = String::class.reflekt().lastMethod { name = "valueOf" }
        assertNotNull(method)
        assertEquals("valueOf", method.name)
    }

    @Test
    fun lastMethodOrNullReturnsNull() {
        val method = String::class.reflekt().lastMethodOrNull { name = "nonexistentMethod" }
        assertNull(method)
    }

    @Test
    fun lastField() {
        // FieldChild has childProp, parentProp, rootProp with superclass=true
        // last should be rootProp (from furthest superclass)
        val field = FieldChild::class.reflekt().lastField {
            superclass = true
        }
        assertNotNull(field)
        assertTrue(field.name in listOf("childProp", "parentProp", "rootProp"))
    }

    @Test
    fun lastConstructor() {
        // StringBuilder has multiple constructors; last should be the most complex
        val ctor = StringBuilder::class.reflekt().lastConstructor { }
        assertNotNull(ctor)
    }

    @Test
    fun instanceLastMethod() {
        val method = "hello".reflekt().lastMethod { name = "valueOf" }
        assertNotNull(method)
        assertEquals("valueOf", method.name)
    }

    // ========== No-arg overloads ==========

    @Test
    fun firstMethodNoArg() {
        val method = String::class.reflekt().firstMethod()
        assertNotNull(method)
    }

    @Test
    fun firstFieldNoArg() {
        val field = FieldSubject::class.reflekt().firstField()
        assertNotNull(field)
    }

    @Test
    fun firstConstructorNoArg() {
        val ctor = StringBuilder::class.reflekt().firstConstructor()
        assertNotNull(ctor)
    }

    @Test
    fun lastMethodNoArg() {
        val method = String::class.reflekt().lastMethod()
        assertNotNull(method)
    }

    @Test
    fun lastFieldNoArg() {
        val field = FieldChild::class.reflekt().lastField()
        assertNotNull(field)
    }

    @Test
    fun lastConstructorNoArg() {
        val ctor = StringBuilder::class.reflekt().lastConstructor()
        assertNotNull(ctor)
    }

    @Test
    fun methodsNoArg() {
        val methods = String::class.reflekt().methods()
        assertTrue(methods.isNotEmpty())
    }

    @Test
    fun fieldsNoArg() {
        val fields = FieldSubject::class.reflekt().fields()
        assertTrue(fields.isNotEmpty())
    }

    @Test
    fun constructorsNoArg() {
        val ctors = StringBuilder::class.reflekt().constructors()
        assertTrue(ctors.isNotEmpty())
    }

    @Test
    fun instanceFirstMethodNoArg() {
        val method = "hello".reflekt().firstMethod()
        assertNotNull(method)
    }

    @Test
    fun instanceMethodsNoArg() {
        val methods = "hello".reflekt().methods()
        assertTrue(methods.isNotEmpty())
    }

    @Test
    fun modifiersSubsetMatch() {
        // Math.sin has modifiers: PUBLIC, STATIC, NATIVE
        // We filter for only STATIC, or only PUBLIC and STATIC, which should match.
        val methodsStatic = Math::class.reflekt().methods {
            modifiers(Modifiers.STATIC)
        }
        assertTrue(methodsStatic.any { it.name == "sin" })

        val methodsPublicStatic = Math::class.reflekt().methods {
            modifiers(Modifiers.PUBLIC, Modifiers.STATIC)
        }
        assertTrue(methodsPublicStatic.any { it.name == "sin" })

        // But filtering for STATIC and FINAL should not match Math.sin
        val methodsStaticFinal = Math::class.reflekt().methods {
            modifiers(Modifiers.STATIC, Modifiers.FINAL)
        }
        assertTrue(methodsStaticFinal.none { it.name == "sin" })
    }

    @Test
    fun modifiersVarargCacheHit() {
        ReflectionCache.clear()
        val m1 = Math::class.reflekt().methods { modifiers(Modifiers.PUBLIC, Modifiers.STATIC) }
        val m2 = Math::class.reflekt().methods { modifiers(Modifiers.PUBLIC, Modifiers.STATIC) }
        assertSame(m1, m2)
    }
}
