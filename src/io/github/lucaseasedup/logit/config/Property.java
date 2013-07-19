package io.github.lucaseasedup.logit.config;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * @author LucasEasedUp
 */
public class Property extends Observable
{
    public Property(String path, PropertyType type, boolean changeRequiresRestart, Object value, Object[] validValues)
    {
        this.path = path;
        this.type = type;
        this.changeRequiresRestart = changeRequiresRestart;
        this.value = value;
        this.validValues = validValues;
    }
    
    public Property(String path, PropertyType type, boolean changeRequiresRestart)
    {
        this(path, type, changeRequiresRestart, null, null);
    }
    
    public String getPath()
    {
        return path;
    }
    
    public PropertyType getType()
    {
        return type;
    }
    
    public boolean changeRequiresRestart()
    {
        return changeRequiresRestart;
    }
    
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        
        sb.append("[").append(type.toString()).append("] ");
        
        switch (type)
        {
        case OBJECT:
        case BOOLEAN:
        case COLOR:
        case DOUBLE:
        case INT:
        case ITEM_STACK:
        case LONG:
        case STRING:
        case VECTOR:
            sb.append(value.toString());
            break;
        case LIST:
        case BOOLEAN_LIST:
        case BYTE_LIST:
        case CHARACTER_LIST:
        case DOUBLE_LIST:
        case FLOAT_LIST:
        case INTEGER_LIST:
        case LONG_LIST:
        case MAP_LIST:
        case SHORT_LIST:
        case STRING_LIST:
            sb.append(StringUtils.join((List) value, ", "));
            break;
        default:
            throw new RuntimeException("Unknown property type.");
        }
        
        return sb.toString();
    }
    
    public Object getValue()
    {
        switch (type)
        {
        case OBJECT:
            return value;
        case BOOLEAN:
            return (Boolean) value;
        case COLOR:
            return (Color) value;
        case DOUBLE:
            return (Double) value;
        case INT:
            return (Integer) value;
        case ITEM_STACK:
            return (ItemStack) value;
        case LONG:
            return (Long) value;
        case STRING:
            return (String) value;
        case VECTOR:
            return (Vector) value;
        case LIST:
            return (List) value;
        case BOOLEAN_LIST:
            return (List<Boolean>) value;
        case BYTE_LIST:
            return (List<Byte>) value;
        case CHARACTER_LIST:
            return (List<Character>) value;
        case DOUBLE_LIST:
            return (List<Double>) value;
        case FLOAT_LIST:
            return (List<Float>) value;
        case INTEGER_LIST:
            return (List<Integer>) value;
        case LONG_LIST:
            return (List<Long>) value;
        case MAP_LIST:
            return (List<Map>) value;
        case SHORT_LIST:
            return (List<Short>) value;
        case STRING_LIST:
            return (List<String>) value;
        default:
            throw new RuntimeException("Unknown property type.");
        }
    }
    
    public boolean getBoolean()
    {
        return (Boolean) value;
    }
    
    public Color getColor()
    {
        return (Color) value;
    }
    
    public double getDouble()
    {
        return (Double) value;
    }
    
    public int getInt()
    {
        return (Integer) value;
    }
    
    public ItemStack getItemStack()
    {
        return (ItemStack) value;
    }
    
    public long getLong()
    {
        return (Long) value;
    }
    
    public String getString()
    {
        return (String) value;
    }
    
    public Vector getVector()
    {
        return (Vector) value;
    }
    
    public List getList()
    {
        return (List) value;
    }
    
    public List<String> getStringList()
    {
        return (List<String>) value;
    }
    
    public void set(Object value) throws InvalidPropertyValueException
    {
        if (validValues != null && !Arrays.asList(validValues).contains(value))
            throw new InvalidPropertyValueException("Invalid value: " + value.toString());
        
        if (!this.value.equals(value))
            setChanged();
        
        this.value = value;
        
        notifyObservers();
    }
    
    private final String path;
    private final PropertyType type;
    private final boolean changeRequiresRestart;
    private Object value = null;
    private final Object[] validValues;
}