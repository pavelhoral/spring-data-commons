/*
 * Copyright 2011-2019 the original author or authors.
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
package org.springframework.data.repository.cdi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Named;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.config.CustomRepositoryImplementationDetector;

/**
 * Unit tests for {@link CdiRepositoryBean}.
 * 
 * @author Oliver Gierke
 */
@RunWith(MockitoJUnitRunner.class)
public class CdiRepositoryBeanUnitTests {

	static final String PASSIVATION_ID = "javax.enterprise.inject.Default:org.springframework.data.repository.cdi.CdiRepositoryBeanUnitTests$SampleRepository";

	static final Set<Annotation> NO_ANNOTATIONS = Collections.emptySet();
	static final Set<Annotation> SINGLE_ANNOTATION = Collections
			.singleton((Annotation) new CdiRepositoryExtensionSupport.DefaultAnnotationLiteral());

	@Mock BeanManager beanManager;

	@Test(expected = IllegalArgumentException.class)
	public void voidRejectsNullQualifiers() {
		new DummyCdiRepositoryBean<>(null, SampleRepository.class, beanManager);
	}

	@Test(expected = IllegalArgumentException.class)
	public void voidRejectsNullRepositoryType() {
		new DummyCdiRepositoryBean<>(NO_ANNOTATIONS, null, beanManager);
	}

	@Test(expected = IllegalArgumentException.class)
	public void voidRejectsNullBeanManager() {
		new DummyCdiRepositoryBean<>(NO_ANNOTATIONS, SampleRepository.class, null);
	}

	@Test
	public void returnsBasicMetadata() {

		DummyCdiRepositoryBean<SampleRepository> bean = new DummyCdiRepositoryBean<>(NO_ANNOTATIONS, SampleRepository.class,
				beanManager);

		assertThat(bean.getBeanClass()).isEqualTo(SampleRepository.class);
		assertThat(bean.getName()).isEqualTo(SampleRepository.class.getName());
		assertThat(bean.isNullable()).isFalse();
	}

	@Test
	public void returnsAllImplementedTypes() {

		DummyCdiRepositoryBean<SampleRepository> bean = new DummyCdiRepositoryBean<>(NO_ANNOTATIONS, SampleRepository.class,
				beanManager);

		Set<Type> types = bean.getTypes();
		assertThat(types).containsExactlyInAnyOrder(SampleRepository.class, Repository.class);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void detectsStereotypes() {

		DummyCdiRepositoryBean<StereotypedSampleRepository> bean = new DummyCdiRepositoryBean<>(NO_ANNOTATIONS,
				StereotypedSampleRepository.class, beanManager);

		assertThat(bean.getStereotypes()).containsExactly(StereotypeAnnotation.class);
	}

	@Test // DATACMNS-299
	@SuppressWarnings("rawtypes")
	public void scopeDefaultsToApplicationScoped() {

		Bean<SampleRepository> bean = new DummyCdiRepositoryBean<>(NO_ANNOTATIONS, SampleRepository.class, beanManager);
		assertThat(bean.getScope()).isEqualTo(ApplicationScoped.class);
	}

	@Test // DATACMNS-322
	public void createsPassivationId() {

		CdiRepositoryBean<SampleRepository> bean = new DummyCdiRepositoryBean<>( //
				SINGLE_ANNOTATION, //
				SampleRepository.class, //
				beanManager //
		);
		assertThat(bean.getId()).isEqualTo(PASSIVATION_ID);
	}

	@Test // DATACMNS-764
	public void passesCorrectBeanNameToTheImplementationDetector() {

		CustomRepositoryImplementationDetector detector = mock(CustomRepositoryImplementationDetector.class);

		CdiRepositoryBean<SampleRepository> bean = new CdiRepositoryBean<SampleRepository>( //
				SINGLE_ANNOTATION, //
				SampleRepository.class, //
				beanManager, //
				Optional.of(detector) //
		) {

			@Override
			protected SampleRepository create( //
					CreationalContext<SampleRepository> creationalContext, //
					Class<SampleRepository> repositoryType, //
					Optional<Object> customImplementation //
			) {
				return null;
			}
		};

		bean.create(mock(CreationalContext.class), SampleRepository.class);

		verify(detector).detectCustomImplementation( //
				eq("CdiRepositoryBeanUnitTests.SampleRepositoryImpl"), //
				eq("namedRepositoryImpl"), //
				anySet(), //
				anySet(), //
				Mockito.any(Function.class) //
		);
	}

	static class DummyCdiRepositoryBean<T> extends CdiRepositoryBean<T> {

		DummyCdiRepositoryBean(Set<Annotation> qualifiers, Class<T> repositoryType, BeanManager beanManager) {
			super(qualifiers, repositoryType, beanManager);
		}

		@Override
		protected T create(CreationalContext<T> creationalContext, Class<T> repositoryType,
				Optional<Object> customImplementation) {
			return null;
		}
	}

	@Named("namedRepository")
	static interface SampleRepository extends Repository<Object, Serializable> {

	}

	@StereotypeAnnotation
	static interface StereotypedSampleRepository {

	}
}
