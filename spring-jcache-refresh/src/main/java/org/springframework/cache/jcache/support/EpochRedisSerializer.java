package org.springframework.cache.jcache.support;

import lombok.Data;
import lombok.NonNull;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.ByteBuffer;
import java.util.Arrays;

@Data
public class EpochRedisSerializer<T> implements RedisSerializer<EpochValueWrapper<T>> {
    private static final byte magicNumber = 0x1;
    private static final byte prefixLength = 9;

    @NonNull
    private RedisSerializer<T> delegate;

    @Override
    public byte[] serialize(EpochValueWrapper<T> wrapper) throws SerializationException {
        byte[] serialized = delegate.serialize(wrapper.get());
        if (serialized == null) {
            return null;
        }
        byte[] bytes = new byte[prefixLength + serialized.length];
        long deadline = wrapper.getEpoch();
        ByteBuffer.wrap(bytes).put(magicNumber).putLong(deadline).put(serialized);
        return bytes;
    }

    @Override
    public EpochValueWrapper<T> deserialize(byte[] bytes) throws SerializationException {
        long deadline;
        if (bytes.length > prefixLength) {
            if (bytes[0] != magicNumber) {
                throw new SerializationException("illegal magic byte for EpochValueWrapper");
            }
            deadline = ByteBuffer.wrap(bytes, 1, prefixLength).getLong();
            bytes = Arrays.copyOfRange(bytes, prefixLength, bytes.length);
        } else {
            throw new SerializationException("illegal format for EpochValueWrapper: " + Arrays.toString(bytes));
        }
        T value = delegate.deserialize(bytes);
        return EpochValueWrapper.<T>builder().epoch(deadline).value(value).build();
    }
}
