package com.grinderwolf.swm.plugin.util;

import com.google.common.reflect.TypeToken;
import lombok.Getter;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class providing Type Parsers for dynamically parsing Strings into various Types
 */
@SuppressWarnings("UnstableApiUsage")
public class TypeParsers {

    private TypeParsers() {
        throw new UnsupportedOperationException("This class cannot be instantiated!");
    }

    @Getter
    private static final TypeParserCollection PARSERS = new TypeParserCollection();

    static {
        PARSERS.registerType(TypeToken.of(String.class), new StringParser());
        PARSERS.registerType(TypeToken.of(Boolean.class), new BooleanParser());
    }

    public static class TypeParserCollection {
        private final Map<TypeToken<?>, TypeParser<?>> typeMatches = new ConcurrentHashMap<>();

        @SuppressWarnings("UnusedReturnValue")
        public <T> TypeParserCollection registerType(TypeToken<T> type, TypeParser<? super T> parser) {
            Objects.requireNonNull(type);
            Objects.requireNonNull(parser);
            typeMatches.put(type, parser);
            return this;
        }

        @SuppressWarnings("unchecked")
        public <T> TypeParser<T> get(TypeToken<T> t) {
            Objects.requireNonNull(t);
            final TypeToken<T> type = t.wrap();
            TypeParser<?> parser = typeMatches.get(type);
            if (parser == null) {
                parser = typeMatches.entrySet().stream().filter(e -> e.getKey().isSupertypeOf(type)).findFirst().map(Map.Entry::getValue).orElse(null);
                typeMatches.put(type, parser);
            }
            return (TypeParser<T>) parser;
        }

    }

    public interface TypeParser<T> {

        T parse(TypeToken<?> type, String value);

    }

    private static class StringParser implements TypeParser<String> {
        @Override
        public String parse(TypeToken<?> type, String value) {
            return value;
        }
    }

    private static class BooleanParser implements TypeParser<Boolean> {
        @Override
        public Boolean parse(TypeToken<?> type, String value) {
            return Boolean.parseBoolean(value);
        }
    }

}
