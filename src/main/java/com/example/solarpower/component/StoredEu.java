package com.example.solarpower.component;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.math.BigInteger;
import java.util.Base64;

/**
 * 掉落物携带的蓄电量组件（储电盒拆机保留电量用）。
 * <p>编解码走 Base64 大端补码字符串：百万位大数的十进制 toString 是平方级开销，禁用。
 */
public record StoredEu(BigInteger value) {

    public static final Codec<StoredEu> CODEC = Codec.STRING.xmap(
            s -> new StoredEu(new BigInteger(Base64.getDecoder().decode(s))),
            v -> Base64.getEncoder().encodeToString(v.value.toByteArray()));

    @SuppressWarnings("unchecked")
    public static final StreamCodec<RegistryFriendlyByteBuf, StoredEu> STREAM_CODEC =
            (StreamCodec<RegistryFriendlyByteBuf, StoredEu>) (Object) ByteBufCodecs.fromCodec(CODEC);
}
