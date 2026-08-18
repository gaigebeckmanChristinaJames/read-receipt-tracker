/*****************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one                *
 * or more contributor license agreements.  See the NOTICE file              *
 * distributed with this work for additional information                     *
 * regarding copyright ownership.  The ASF licenses this file                *
 * to you under the Apache License, Version 2.0 (the                         *
 * "License"); you may not use this file except in compliance                *
 * with the License.  You may obtain a copy of the License at                *
 *                                                                           *
 *     http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing,                *
 * software distributed under the License is distributed on an               *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY                    *
 * KIND, either express or implied.  See the License for the                 *
 * specific language governing permissions and limitations                   *
 * under the License.                                                        *
 *                                                                           *
 *                                                                           *
 * This file is part of the BeanShell Java Scripting distribution.           *
 * Documentation and updates may be found at http://www.beanshell.org/       *
 * Patrick Niemeyer (pat@pat.net)                                            *
 * Author of Learning Java, O'Reilly & Associates                            *
 *                                                                           *
 *****************************************************************************/



package bsh;

import java.lang.reflect.Array;

public class BSHType extends SimpleNode implements BshClassManager.Listener {
    private static final long serialVersionUID = 1L;

    /**
        baseType is used during evaluation of full type and retained for the
        case where we are an array type.
        In the case where we are not an array this will be the same as type.
    */
    private Class<?> baseType;
    /**
        If we are an array type this will be non zero and indicate the
        dimensionality of the array.  e.g. 2 for String[][];
    */
    private int arrayDims;

    /**
        Internal cache of the type.  Cleared on classloader change.
    */
    private Class<?> type;

    /** Flag to track if instance is already a listener */
    private boolean isListener = false;

    String descriptor;

    BSHType(int id) {
        super(id);
    }

    /**
        Used by the grammar to indicate dimensions of array types
        during parsing.
    */
    public void addArrayDimension() {
        arrayDims++;
    }

    Node getTypeNode() {
        return jjtGetChild(0);
    }

    /**
         Returns a class descriptor for this type.
         If the type is an ambiguous name (object type) evaluation is
         attempted through the namespace in order to resolve imports.
         If it is not found and the name is non-compound we assume the default
         package for the name.
    */
    public String getTypeDescriptor(
        CallStack callstack, Interpreter interpreter, String defaultPackage )
    {
        // return cached type if available
        if ( descriptor != null )
            return descriptor;

        String descriptor;
        //  first node will either be PrimitiveType or AmbiguousName
        Node node = getTypeNode();
        if ( node instanceof BSHPrimitiveType )
            descriptor = getTypeDescriptor( ((BSHPrimitiveType)node).type );
        else
        {
            String clasName = ((BSHAmbiguousName)node).text;
            String innerClass = callstack.top().importedClasses.get(clasName);

            Class<?> clas = null;
            if ( innerClass == null ) try {
                clas = ((BSHAmbiguousName)node).toClass(
                    callstack, interpreter );
            } catch ( EvalError e ) {
                // Lets assume we have a generics raw type
                if (clasName.length() == 1)
                    clasName = "java.lang.Object";
            } else
                clasName = innerClass.replace('.', '$');

            if ( clas != null ) {
                descriptor = getTypeDescriptor( clas );
            } else {
                // The type could not be resolved to a Class.  For a compound
                // name this may be a nested (inner) class reference such as
                // "de.robv.android.xposed.XC_MethodHook.MethodHookParam", whose
                // binary name uses '$' at the inner-class boundary. A naive
                // '.'->'/' conversion would emit an unresolvable descriptor
                // (...XC_MethodHook/MethodHookParam;), so try to recover the
                // correct '$'-based descriptor first.
                String nested = nestedTypeDescriptor( clasName, callstack );
                if ( nested != null )
                    descriptor = nested;
                else if ( defaultPackage == null || Name.isCompound( clasName ) )
                    descriptor = "L" + clasName.replace('.','/') + ";";
                else
                    descriptor =
                        "L"+defaultPackage.replace('.','/')+"/"+clasName + ";";
            }
        }

        for(int i=0; i<arrayDims; i++)
            descriptor = "["+descriptor;

        this.descriptor = descriptor;
        return descriptor;
    }

    /**
        Attempt to build a class descriptor for a compound name that denotes a
        nested (inner) class, e.g. "java.util.Map.Entry" or
        "de.robv.android.xposed.XC_MethodHook.MethodHookParam".

        We find the longest leading dotted prefix that resolves to a Class (the
        outer class) through the current namespace and join the remaining parts
        with '$' to form the binary name.  Returns the "L...;" descriptor on
        success, or null if no such outer class can be resolved.  This keeps the
        inner-class boundary as '$' rather than corrupting it to '/'.
    */
    private static String nestedTypeDescriptor( String clasName, CallStack callstack )
    {
        if ( callstack == null || clasName == null
                || clasName.indexOf('.') < 0 )
            return null;

        NameSpace ns = callstack.top();
        if ( ns == null )
            return null;

        String[] parts = clasName.split("\\.");
        // Try progressively shorter prefixes as the candidate outer class,
        // longest first so the most specific (deepest) class wins.
        for ( int outer = parts.length - 1; outer >= 1; outer-- ) {
            StringBuilder outerName = new StringBuilder(parts[0]);
            for ( int i = 1; i < outer; i++ )
                outerName.append('.').append(parts[i]);

            Class<?> clas = null;
            try {
                clas = ns.getClass( outerName.toString() );
            } catch ( UtilEvalError e ) { /* try a shorter prefix */ }

            if ( clas != null ) {
                StringBuilder binary = new StringBuilder( clas.getName() );
                for ( int i = outer; i < parts.length; i++ )
                    binary.append('$').append(parts[i]);
                return "L" + binary.toString().replace('.','/') + ";";
            }
        }
        return null;
    }

    public Class<?> getType( CallStack callstack, Interpreter interpreter )
        throws EvalError
    {
        // return cached type if available
        if ( type != null )
            return type;

        //  first node will either be PrimitiveType or AmbiguousName
        Node node = getTypeNode();
        if ( node instanceof BSHPrimitiveType )
            baseType = ((BSHPrimitiveType)node).getType();
        else
            try {
            baseType = ((BSHAmbiguousName)node).toClass(
                callstack, interpreter );
            } catch (EvalError e) {
                // Assuming generics raw type
                if (node.getText().trim().length() == 1
                        && e.getCause() instanceof ClassNotFoundException)
                    baseType = Object.class;
                else
                    throw e; // roll up unhandled error
            }

        if ( arrayDims > 0 ) {
            try {
                // Get the type by constructing a prototype array with
                // arbitrary (zero) length in each dimension.
                int[] dims = new int[arrayDims]; // int array default zeros
                Object obj = Array.newInstance(
                        null == baseType ? Object.class : baseType, dims);
                type = obj.getClass();
            } catch(Exception e) {
                throw new EvalException("Couldn't construct array type",
                    this, callstack, e);
            }
        } else
            type = baseType;

        // add listener to reload type if class is reloaded see #699
        if (!isListener) { // only add once
            interpreter.getClassManager().addListener(this);
            isListener = true;
        }

        return type;
    }

    /**
        baseType is used during evaluation of full type and retained for the
        case where we are an array type.
        In the case where we are not an array this will be the same as type.
    */
    public Class<?> getBaseType() {
        return baseType;
    }
    /**
        If we are an array type this will be non zero and indicate the
        dimensionality of the array.  e.g. 2 for String[][];
    */
    public int getArrayDims() {
        return arrayDims;
    }

    /**
     * Get the type name as a string without evaluation context.
     * For primitive types returns the Java keyword (e.g. "int").
     * For object types returns the text of the ambiguous name.
     * Array dimensions are appended as {@code []}.
     */
    public String getTypeText() {
        Node node = getTypeNode();
        String name;
        if (node instanceof BSHPrimitiveType) {
            Class<?> cls = ((BSHPrimitiveType) node).type;
            if (cls == Boolean.TYPE) name = "boolean";
            else if (cls == Character.TYPE) name = "char";
            else if (cls == Byte.TYPE) name = "byte";
            else if (cls == Short.TYPE) name = "short";
            else if (cls == Integer.TYPE) name = "int";
            else if (cls == Long.TYPE) name = "long";
            else if (cls == Float.TYPE) name = "float";
            else if (cls == Double.TYPE) name = "double";
            else if (cls == Void.TYPE) name = "void";
            else name = cls.getName();
        } else if (node instanceof BSHAmbiguousName) {
            name = ((BSHAmbiguousName) node).text;
        } else {
            name = "?";
        }
        for (int i = 0; i < arrayDims; i++)
            name += "[]";
        return name;
    }

    /** Clear instance cache to reload types on class loader change #699 */
    public void classLoaderChanged() {
        type = null;
        baseType = null;
    }

    public static String getTypeDescriptor( Class<?> clas )
    {
        if ( clas == Boolean.TYPE ) return "Z";
        if ( clas == Character.TYPE ) return "C";
        if ( clas == Byte.TYPE ) return "B";
        if ( clas == Short.TYPE ) return "S";
        if ( clas == Integer.TYPE ) return "I";
        if ( clas == Long.TYPE ) return "J";
        if ( clas == Float.TYPE ) return "F";
        if ( clas == Double.TYPE ) return "D";
        if ( clas == Void.TYPE ) return "V";

        String name = clas.getName().replace('.','/');

        if ( name.startsWith("[") || name.endsWith(";") )
            return name;
        else
            return "L"+ name.replace('.','/') +";";
    }
}
