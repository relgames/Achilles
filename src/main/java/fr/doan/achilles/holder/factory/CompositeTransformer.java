package fr.doan.achilles.holder.factory;

import me.prettyprint.hector.api.beans.Composite;
import me.prettyprint.hector.api.beans.HColumn;

import com.google.common.base.Function;

import fr.doan.achilles.entity.PropertyHelper;
import fr.doan.achilles.entity.metadata.PropertyMeta;
import fr.doan.achilles.holder.KeyValue;

/**
 * TransformerBuilder
 * 
 * @author DuyHai DOAN
 * 
 */
public class CompositeTransformer
{

	private PropertyHelper helper = new PropertyHelper();

	public <K, V> Function<HColumn<Composite, ?>, K> buildKeyTransformer(
			final PropertyMeta<K, V> propertyMeta)
	{

		return new Function<HColumn<Composite, ?>, K>()
		{
			public K apply(HColumn<Composite, ?> hColumn)
			{
				return buildKeyFromComposite(propertyMeta, hColumn);
			}
		};
	}

	public <K, V> Function<HColumn<Composite, ?>, V> buildValueTransformer(
			final PropertyMeta<K, V> propertyMeta)
	{

		return new Function<HColumn<Composite, ?>, V>()
		{
			public V apply(HColumn<Composite, ?> hColumn)
			{
				return propertyMeta.getValue(hColumn.getValue());
			}
		};
	}

	public <K, V> Function<HColumn<Composite, ?>, ?> buildRawValueTransformer(
			final PropertyMeta<K, V> propertyMeta)
	{

		return new Function<HColumn<Composite, ?>, Object>()
		{
			public Object apply(HColumn<Composite, ?> hColumn)
			{
				return hColumn.getValue();
			}
		};
	}

	public <K, V> Function<HColumn<Composite, ?>, Integer> buildTtlTransformer(
			final PropertyMeta<K, V> propertyMeta)
	{

		return new Function<HColumn<Composite, ?>, Integer>()
		{
			public Integer apply(HColumn<Composite, ?> hColumn)
			{
				return hColumn.getTtl();
			}
		};
	}

	public <K, V> Function<HColumn<Composite, ?>, KeyValue<K, V>> buildKeyValueTransformer(
			final PropertyMeta<K, V> propertyMeta)
	{

		return new Function<HColumn<Composite, ?>, KeyValue<K, V>>()
		{
			public KeyValue<K, V> apply(HColumn<Composite, ?> hColumn)
			{
				return buildKeyValueFromComposite(propertyMeta, hColumn);
			}
		};
	}

	public <K, V> KeyValue<K, V> buildKeyValueFromComposite(PropertyMeta<K, V> propertyMeta,
			HColumn<Composite, ?> hColumn)
	{
		K key = buildKeyFromComposite(propertyMeta, hColumn);
		V value = propertyMeta.getValue(hColumn.getValue());
		int ttl = hColumn.getTtl();

		return new KeyValue<K, V>(key, value, ttl);
	}

	public <K, V> K buildKeyFromComposite(PropertyMeta<K, V> propertyMeta,
			HColumn<Composite, ?> hColumn)
	{
		K key;
		if (propertyMeta.isSingleKey())
		{
			key = (K) hColumn.getName().get(0, propertyMeta.getKeySerializer());
		}
		else
		{
			key = helper.buildMultiKeyForComposite(propertyMeta, hColumn.getName().getComponents());
		}
		return key;
	}
}
