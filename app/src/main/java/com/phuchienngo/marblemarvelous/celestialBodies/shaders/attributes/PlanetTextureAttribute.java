package com.phuchienngo.marblemarvelous.celestialBodies.shaders.attributes;

import com.badlogic.gdx.graphics.Cubemap;
import com.badlogic.gdx.graphics.g3d.Attribute;
import com.badlogic.gdx.graphics.g3d.attributes.CubemapAttribute;
import com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.GdxRuntimeException;

/* JADX INFO: loaded from: classes.dex */
public class PlanetTextureAttribute extends Attribute {
    public final Matrix4 extraTransform;
    public final TextureDescriptor<Cubemap> textureDescription;
    public static final long DayDiffuse = register("DayDiffuse");
    public static final long NightDiffuse = register("NightDiffuse");
    public static final long StormsDiffuse = register("StormsDiffuse");
    public static long DayNormals = register("DayNormals");
    public static long CloudDiffuse = register("CloudDiffuse");
    private static long Mask = ((((CubemapAttribute.EnvironmentMap | DayDiffuse) | NightDiffuse) | DayNormals) | CloudDiffuse) | StormsDiffuse;

    public static boolean is(long mask) {
        return (Mask & mask) != 0;
    }

    PlanetTextureAttribute(long type) {
        super(type);
        this.extraTransform = new Matrix4().idt();
        if (!is(type)) {
            throw new GdxRuntimeException("Invalid type specified");
        }
        this.textureDescription = new TextureDescriptor<>();
    }

    <T extends Cubemap> PlanetTextureAttribute(long type, TextureDescriptor<T> textureDescription, Matrix4 transform) {
        this(type);
        this.extraTransform.set(transform);
        this.textureDescription.set(textureDescription);
    }

    PlanetTextureAttribute(long type, Cubemap texture, Matrix4 transform) {
        this(type);
        this.extraTransform.set(transform);
        this.textureDescription.texture = texture;
    }

    PlanetTextureAttribute(PlanetTextureAttribute copyFrom) {
        this(copyFrom.type, copyFrom.textureDescription, copyFrom.extraTransform);
    }

    @Override // com.badlogic.gdx.graphics.g3d.Attribute
    public Attribute copy() {
        return new PlanetTextureAttribute(this);
    }

    @Override // com.badlogic.gdx.graphics.g3d.Attribute
    public int hashCode() {
        int result = super.hashCode();
        return (967 * result) + this.textureDescription.hashCode();
    }

    @Override // java.lang.Comparable
    public int compareTo(Attribute o) {
        if (this.type != o.type) {
            return (int) (this.type - o.type);
        }
        return this.textureDescription.compareTo((TextureDescriptor) ((CubemapAttribute) o).textureDescription);
    }

    public static PlanetTextureAttribute createDay(Cubemap texture) {
        return new PlanetTextureAttribute(DayDiffuse, texture, new Matrix4().idt());
    }

    public static PlanetTextureAttribute createNight(Cubemap texture) {
        return new PlanetTextureAttribute(NightDiffuse, texture, new Matrix4().idt());
    }

    public static PlanetTextureAttribute createStorms(Cubemap texture) {
        return new PlanetTextureAttribute(StormsDiffuse, texture, new Matrix4().idt());
    }

    public static PlanetTextureAttribute createNormals(Cubemap texture) {
        return new PlanetTextureAttribute(DayNormals, texture, new Matrix4().idt());
    }

    public static Attribute createClouds(Cubemap texture) {
        return new PlanetTextureAttribute(CloudDiffuse, texture, new Matrix4().idt());
    }
}
