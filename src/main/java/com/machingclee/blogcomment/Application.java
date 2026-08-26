package com.machingclee.blogcomment;

import com.machingclee.domain.util.common.query.DefaultQueryInvoker;
import com.machingclee.domain.util.common.query.interfaces.QueryHandler;
import com.machingclee.domain.util.common.query.interfaces.QueryInvoker;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.util.List;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * Explicitly register a CharacterEncodingFilter bean so that the response
     * character encoding is forced to UTF-8 even when running inside
     * {@code aws-serverless-java-container}. The container reads the servlet
     * response bytes to build the {@code AwsProxyResponse} body string, and
     * without this bean the servlet default (ISO-8859-1) may be used, garbling
     * multi-byte characters like Chinese names.
     */
    @Bean
    public CharacterEncodingFilter characterEncodingFilter() {
        CharacterEncodingFilter filter = new CharacterEncodingFilter();
        filter.setEncoding("UTF-8");
        filter.setForceEncoding(true);
        return filter;
    }

    /**
     * Fallback {@link QueryInvoker} bean that activates when the domain-util
     * auto-configuration's {@code defaultQueryInvoker} is not created.
     *
     * <p>{@code DomainUtilAutoConfiguration.defaultQueryInvoker} is gated by
     * {@code @ConditionalOnBean(QueryHandler.class)}. In the Lambda / SnapStart
     * environment the condition sometimes fails even though the application's
     * {@code QueryHandler} beans are fully registered, leaving no
     * {@code QueryInvoker} in the context and causing every controller that
     * depends on it to throw {@code NoSuchBeanDefinitionException}.</p>
     *
     * <p>This bean mirrors the auto-configuration one exactly but uses
     * {@code @ConditionalOnMissingBean} instead — it only activates as a
     * safety net when no {@code QueryInvoker} was registered. When the
     * auto-configuration succeeds this bean quietly backs off, so there is
     * never a duplicate-bean conflict.</p>
     */
    @Bean
    @ConditionalOnMissingBean(QueryInvoker.class)
    public DefaultQueryInvoker queryInvoker(List<QueryHandler<?, ?>> queryHandlers,
                                             ApplicationContext context) {
        return new DefaultQueryInvoker(queryHandlers, context);
    }
}
