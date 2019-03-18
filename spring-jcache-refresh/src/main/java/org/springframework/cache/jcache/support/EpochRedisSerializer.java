package org.springframework.cache.jcache.support;

import lombok.Data;
import lombok.NonNull;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.ByteBuffer;

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
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long deadline = 0;
        if (buffer.get() == prefix) {
            deadline = buffer.getLong();
        } else {
            buffer.flip();
        }
        T value = delegate.deserialize(buffer.array());
        if (value == null) {
            return null;
        }
        return EpochValueWrapper.<T>builder().epoch(deadline).value(value).build();
    }
}
