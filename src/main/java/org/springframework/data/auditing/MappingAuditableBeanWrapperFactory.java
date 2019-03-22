/*
 * Copyright 2014-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.auditing;

import java.time.temporal.TemporalAccessor;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Auditable;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.context.PersistentEntities;
import org.springframework.data.util.Optionals;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * {@link AuditableBeanWrapperFactory} that will create am {@link AuditableBeanWrapper} using mapping information
 * obtained from a {@link MappingContext} to detect auditing configuration and eventually invoking setting the auditing
 * values.
 *
 * @author Oliver Gierke
 * @author Christoph Strobl
 * @since 1.8
 */
public class MappingAuditableBeanWrapperFactory extends DefaultAuditableBeanWrapperFactory {

	private final PersistentEntities entities;
	private final Map<Class<?>, MappingAuditingMetadata> metadataCache;

	/**
	 * Creates a new {@link MappingAuditableBeanWrapperFactory} using the given {@link PersistentEntities}.
	 *
	 * @param entities must not be {@literal null}.
	 */
	public MappingAuditableBeanWrapperFactory(PersistentEntities entities) {

		Assert.notNull(entities, "PersistentEntities must not be null!");

		this.entities = entities;
		this.metadataCache = new ConcurrentReferenceHashMap<>();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.auditing.AuditableBeanWrapperFactory#getBeanWrapperFor(java.lang.Object)
	 */
	@Override
	public Optional<AuditableBeanWrapper> getBeanWrapperFor(Object source) {

		return Optional.of(source).flatMap(it -> {

			if (it instanceof Auditable) {
				return super.getBeanWrapperFor(source);
			}

			Class<?> type = it.getClass();

			return entities.getPersistentEntity(type).map(entity -> {

				MappingAuditingMetadata metadata = metadataCache.computeIfAbsent(type,
						key -> new MappingAuditingMetadata(entity));

				return Optional.<AuditableBeanWrapper> ofNullable(
						metadata.isAuditable() ? new MappingMetadataAuditableBeanWrapper(entity.getPropertyAccessor(it), metadata)
								: null);

			}).orElseGet(() -> super.getBeanWrapperFor(source));
		});
	}

	/**
	 * Captures {@link PersistentProperty} instances equipped with auditing annotations.
	 *
	 * @author Oliver Gierke
	 * @since 1.8
	 */
	static class MappingAuditingMetadata {

		private final Optional<? extends PersistentProperty<?>> createdByProperty, createdDateProperty,
				lastModifiedByProperty, lastModifiedDateProperty;

		/**
		 * Creates a new {@link MappingAuditingMetadata} instance from the given {@link PersistentEntity}.
		 *
		 * @param entity must not be {@literal null}.
		 */
		public MappingAuditingMetadata(PersistentEntity<?, ? extends PersistentProperty<?>> entity) {

			Assert.notNull(entity, "PersistentEntity must not be null!");

			this.createdByProperty = Optional.ofNullable(entity.getPersistentProperty(CreatedBy.class));
			this.createdDateProperty = Optional.ofNullable(entity.getPersistentProperty(CreatedDate.class));
			this.lastModifiedByProperty = Optional.ofNullable(entity.getPersistentProperty(LastModifiedBy.class));
			this.lastModifiedDateProperty = Optional.ofNullable(entity.getPersistentProperty(LastModifiedDate.class));
		}

		/**
		 * Returns whether the {@link PersistentEntity} is auditable at all (read: any of the auditing annotations is
		 * present).
		 *
		 * @return
		 */
		public boolean isAuditable() {
			return Optionals.isAnyPresent(createdByProperty, createdDateProperty, lastModifiedByProperty,
					lastModifiedDateProperty);
		}
	}

	/**
	 * {@link AuditableBeanWrapper} using {@link MappingAuditingMetadata} and a {@link PersistentPropertyAccessor} to set
	 * values on auditing properties.
	 *
	 * @author Oliver Gierke
	 * @since 1.8
	 */
	static class MappingMetadataAuditableBeanWrapper extends DateConvertingAuditableBeanWrapper {

		private final PersistentPropertyAccessor accessor;
		private final MappingAuditingMetadata metadata;

		/**
		 * Creates a new {@link MappingMetadataAuditableBeanWrapper} for the given target and
		 * {@link MappingAuditingMetadata}.
		 *
		 * @param accessor must not be {@literal null}.
		 * @param metadata must not be {@literal null}.
		 */
		public MappingMetadataAuditableBeanWrapper(PersistentPropertyAccessor accessor, MappingAuditingMetadata metadata) {

			Assert.notNull(accessor, "PersistentPropertyAccessor must not be null!");
			Assert.notNull(metadata, "Auditing metadata must not be null!");

			this.accessor = accessor;
			this.metadata = metadata;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.auditing.AuditableBeanWrapper#setCreatedBy(java.util.Optional)
		 */
		@Override
		public Object setCreatedBy(Object value) {

			metadata.createdByProperty.ifPresent(it -> this.accessor.setProperty(it, value));

			return value;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.auditing.AuditableBeanWrapper#setCreatedDate(java.util.Optional)
		 */
		@Override
		public TemporalAccessor setCreatedDate(TemporalAccessor value) {
			return setDateProperty(metadata.createdDateProperty, value);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.auditing.AuditableBeanWrapper#setLastModifiedBy(java.util.Optional)
		 */
		@Override
		public Object setLastModifiedBy(Object value) {
			return setProperty(metadata.lastModifiedByProperty, value);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.auditing.AuditableBeanWrapper#getLastModifiedDate()
		 */
		@Override
		public Optional<TemporalAccessor> getLastModifiedDate() {
			return getAsTemporalAccessor(metadata.lastModifiedDateProperty.map(accessor::getProperty),
					TemporalAccessor.class);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.auditing.AuditableBeanWrapper#setLastModifiedDate(java.util.Optional)
		 */
		@Override
		public TemporalAccessor setLastModifiedDate(TemporalAccessor value) {
			return setDateProperty(metadata.lastModifiedDateProperty, value);
		}

		private <T, P extends PersistentProperty<?>> T setProperty(Optional<P> property, T value) {

			property.ifPresent(it -> this.accessor.setProperty(it, value));

			return value;
		}

		private <P extends PersistentProperty<?>> TemporalAccessor setDateProperty(Optional<P> property,
				TemporalAccessor value) {

			property
					.ifPresent(it -> this.accessor.setProperty(it, getDateValueToSet(value, it.getType(), accessor.getBean())));

			return value;
		}
	}
}
