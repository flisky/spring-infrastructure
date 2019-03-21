package org.springframework.cache.jcache.support;

import lombok.Data;
import lombok.NonNull;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.ByteBuffer;
import java.util.Arrays;

@Data
public class EpochRedisSerializer<T> implements RedisSerializer<EpochValueWrapper<T>> {
    private static final byte prefix = 0x1;

    @NonNull
    private RedisSerializer<T> delegate;

    @Override
    public byte[] serialize(EpochValueWrapper<T> wrapper) throws SerializationException {
        byte[] serialized = delegate.serialize(wrapper.get());
        if (serialized == null) {
            return null;
        }
        byte[] bytes = new byte[9 + serialized.length];
        long deadline = wrapper.getEpoch();
        ByteBuffer.wrap(bytes).put(prefix).putLong(deadline).put(serialized);
        return bytes;
    }

    @Override
    public EpochValueWrapper<T> deserialize(byte[] bytes) throws SerializationException {
        long deadline = 0;
        if (bytes.length > 8 && bytes[0] == prefix) {
            deadline = ByteBuffer.wrap(bytes, 1, 9).getLong();
            bytes = Arrays.copyOfRange(bytes, 9, bytes.length);
        }
        T value = delegate.deserialize(bytes);
        if (value == null) {
            return null;
        }
        return EpochValueWrapper.<T>builder().epoch(deadline).value(value).build();
    }
}
