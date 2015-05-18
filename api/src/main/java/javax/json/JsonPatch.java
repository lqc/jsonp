/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package javax.json;

import java.util.ArrayList;
import javax.json.JsonValue.ValueType;

/**
 * This class is an immutable representation of a JSON Patch as specified in
 * <a href="http://tools.ietf.org/html/rfc6902">RFC 6902</a>.
 * <p>A {@code JsonPatch} can be instantiated with {@link #JsonPatch(JsonArray)}
 * by specifying the patch operations in a JSON Patch. Alternately, it
 * can also be constructed with a {@link JsonPatchBuilder}.
 * </p>
 * The following illustrates both approaches.
 * <p>1. Construct a JsonPatch with a JSON Patch.
 * <pre>{@code
 *   JsonArray contacts = ... // The target to be patched
 *   JsonArray patch = ...  ; // JSON Patch
 *   JsonPatch jsonpatch = new JsonPatch(patch);
 *   JsonArray result = jsonpatch.apply(contacts);
 * } </pre>
 * 2. Construct a JsonPatch with JsonPatchBuilder.
 * <pre>{@code
 *   JsonPatchBuilder builder = new JsonPatchBuilder();
 *   JsonArray result = builder.add("/John/phones/office", "1234-567")
 *                             .remove("/Amy/age")
 *                             .apply(contacts);
 * } </pre>
 */

public class JsonPatch {

    private final JsonArray patch;

    /**
     * Constructs a JsonPatch
     * @param patch the JSON Patch
     */
    public JsonPatch(JsonArray patch) {
        this.patch = patch;
    }

    /**
     * Compares this {@code JsonPatch} with another object.
     * @param obj the object to compare this {@code JsonPatch} against
     * @return true if the given object is a {@code JsonPatch} with the same
     * reference tokens as this one, false otherwise.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || obj.getClass() != JsonPatch.class)
            return false;
        return patch.equals(((JsonPatch)obj).patch);
    }

    /**
     * Returns the hash code value for this {@code JsonPatch}.
     *
     * @return the hash code value for this {@code JsonPatch} object
     */
    @Override
    public int hashCode() {
        return patch.hashCode();
    }

    /**
     * Returns the JSON Patch text
     * @return the JSON Patch text
     */
    @Override
    public String toString() {
        return patch.toString();
    }

    /**
     * Applies the patch operations to the specified {@code target}.
     * The target is not modified by the patch.
     *
     * @param target the target to apply the patch operations
     * @return the transformed target after the patch
     * @throws JsonException if the supplied JSON Patch is malformed or if
     *    it contains references to non-existing members
     */
    public JsonStructure apply(JsonStructure target) {

        JsonStructure result = target;

        for (JsonValue operation: patch) {
            if (operation.getValueType() != ValueType.OBJECT) {
                throw new JsonException("A JSON patch must be an array of JSON objects.");
            }
            result = apply(result, (JsonObject) operation);
        }
        return result;
    }

    /**
     * Applies the patch operations to the specified {@code target}.
     *
     * @param target the target to apply the patch operations
     * @return the transformed target after the patch
     * @throws JsonException if the supplied JSON Patch is malformed or if
     *    it contains references to non-existing members
     */
    public JsonObject apply(JsonObject target) {
        return (JsonObject) apply((JsonStructure)target);
    }

    /**
     * Applies the patch operations to the specified {@code target}.
     *
     * @param target the target to apply the patch operations
     * @return the transformed target after the patch
     * @throws JsonException if the supplied JSON Patch is malformed or if
     *    it contains references to non-existing members
     */
    public JsonArray apply(JsonArray target) {
        return (JsonArray) apply((JsonStructure)target);
    }

    /**
     * Generates a JSON Patch from the source and target {@code JsonStructure}.
     * The generated JSON Patch need not be unique.
     * @param source the source
     * @param target the target, must be the same type as the source
     * @return a JSON Patch which when applied to the source, yields the target
     */
    public static JsonArray diff(JsonStructure source, JsonStructure target) {
        return (new DiffGenerator()).diff(source, target);
    }

    /**
     * Applies a JSON Patch operation to the target.
     * @param target the target to apply the operation
     * @param operation the JSON Patch operation
     * @return the target after the patch
     */
    private JsonStructure apply(JsonStructure target, JsonObject operation) {

        JsonPointer pointer = getPointer(operation, "path");
        JsonPointer from;
        switch (operation.getString("op")) {
            case "add":
                return pointer.add(target, getValue(operation));
            case "replace":
                return pointer.replace(target, getValue(operation));
            case "remove":
                return pointer.remove(target);
            case "copy":
                from = getPointer(operation, "from");
                return pointer.add(target, from.getValue(target));
            case "move":
                from = getPointer(operation, "from");
                if (pointer.equals(from)) {
                    // nop
                    return target;
                }
                // Check if from is a proper prefix of path
                if (operation.getString("path").startsWith(operation.getString("from"))){
                    throw new JsonException("The 'from' path of the patch operation "
                         + "'move' is a proper prefix of the 'path' path");
                }
                return pointer.add(from.remove(target), from.getValue(target));
            case "test":
                if (! getValue(operation).equals(pointer.getValue(target))) {
                    throw new JsonException("The JSON patch operation 'test' failed.");
                }
                return target;
            default:
                throw new JsonException("Illegal value for the op member of the JSON patch operation: " + operation.getString("op"));
        }
    }

    private JsonPointer getPointer(JsonObject operation, String member) {
        JsonString pointerString = operation.getJsonString(member);
        if (pointerString == null) {
            missingMember(operation.getString("op"), member);
        }
        return new JsonPointer(pointerString.getString());
    }

    private JsonValue getValue(JsonObject operation) {
        JsonValue value = operation.get("value");
        if (value == null) {
            missingMember(operation.getString("op"), "value");
        }
        return value;
    }

    private void missingMember(String op, String  member) {
        throw new JsonException(String.format("The JSON Patch operation %s must contain a %s member", op, member));
    }

    static class DiffGenerator {
        private JsonPatchBuilder builder;

        JsonArray diff(JsonStructure source, JsonStructure target) {
            builder = new JsonPatchBuilder();
            diff("", source, target);
            return builder.build();
        }

        private void diff(String path, JsonValue source, JsonValue target) {
            if (source.equals(target)) {
                return;
            }
            ValueType s = source.getValueType();
            ValueType t = target.getValueType();
            if (s == ValueType.OBJECT && t == ValueType.OBJECT) {
                diffObject(path, (JsonObject) source, (JsonObject) target);
            } else if (s == ValueType.ARRAY && t == ValueType.ARRAY) {
                diffArray(path, (JsonArray) source, (JsonArray) target);
            } else {
                builder.replace(path, target);
            }
        }

        private void diffObject(String path, JsonObject source, JsonObject target) {
            source.forEach((key, value) -> {
                if (target.containsKey(key)) {
                    diff(path + '/' + key, value, target.get(key));
                } else {
                    builder.remove(path + '/' + key);
                }
            });
            target.forEach((key, value) -> {
                if (! source.containsKey(key)) {
                    builder.add(path + '/' + key, value);
                }
            });
        }

        /*
         * For array element diff, find the longest common subsequence, per
         * http://en.wikipedia.org/wiki/Longest_common_subsequence_problem .
         * Note that only add and remove operations are generated here. It may be
         * possible to generate move and replace operations by examining the patch
         * operations.
         */
        private static int[][] c;
        private static String curPath;
        private static JsonArray curSource;
        private static JsonArray curTarget;

        private void diffArray(String path, JsonArray source, JsonArray target) {
            curPath = path;
            curTarget = target;

            /* The array c keeps track of length of the subsequence. To avoid
             * computing the equality of array elements again in genPatch, we
             * left shift its value by 1, and use the low order bit to
             * to mark for the equalilty.
             */ 
            int m = source.size();
            int n = target.size();
            c = new int[m+1][n+1];
            for (int i = 0; i < m+1; i++)
                c[i][0] = 0;
            for (int i = 0; i < n+1; i++)
                c[0][i] = 0;
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    if (source.get(i).equals(target.get(j))) {
                        c[i+1][j+1] = ((c[i][j]) & ~1) + 3;
                        // 3 = (1 << 1) & 1;
                    } else {
                        c[i+1][j+1] = Math.max(c[i+1][j], c[i][j+1]) & ~1;
                    }
                }
            }
            genPatch(m, n);
        }

        private void genPatch(int i, int j) {
            if (i > 0 && j > 0 && (c[i][j] & 1) == 1) {
                genPatch(i-1, j-1);
            } else if (j > 0 && (i == 0 || c[i][j-1] >= c[i-1][j])) {
                builder.add(curPath + '/' + (j-1), curTarget.get(j-1));
                genPatch(i, j-1);
            } else if (i > 0 && (j == 0 || c[i][j-1] < c[i-1][j])) {
                builder.remove(curPath + '/' + (i-1));
                genPatch(i-1, j);
            }
        }
    }
}
