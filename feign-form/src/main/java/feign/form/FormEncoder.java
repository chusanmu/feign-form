/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package feign.form;

import static feign.form.util.CharsetUtil.UTF_8;
import static feign.form.util.PojoUtil.isUserPojo;
import static feign.form.util.PojoUtil.toMap;
import static java.util.Arrays.asList;
import static lombok.AccessLevel.PRIVATE;

import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import lombok.experimental.FieldDefaults;
import lombok.val;

/**
 * TODO: 对于表单特殊支持的编码器
 * @author Artem Labazin
 */
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class FormEncoder implements Encoder {

  private static final String CONTENT_TYPE_HEADER;

  private static final Pattern CHARSET_PATTERN;

  static {
    CONTENT_TYPE_HEADER = "Content-Type";
    CHARSET_PATTERN = Pattern.compile("(?<=charset=)([\\w\\-]+)");
  }

  Encoder delegate;

  /**
   * TODO: 每个contentType,对应着各自的处理器
   */
  Map<ContentType, ContentProcessor> processors;

  /**
   * Constructor with the default Feign's encoder as a delegate.
   */
  public FormEncoder () {
    this(new Encoder.Default());
  }

  /**
   * Constructor with specified delegate encoder.
   *
   * @param delegate  delegate encoder, if this encoder couldn't encode object.
   */
  public FormEncoder (Encoder delegate) {
    this.delegate = delegate;

    /**
     * TODO: 注意这里注册了两个处理器，一个用于处理 MULTIPART，另一个用于处理 URLENCODED
     */
    val list = asList(
        new MultipartFormContentProcessor(delegate),
        new UrlencodedFormContentProcessor()
    );

    processors = new HashMap<ContentType, ContentProcessor>(list.size(), 1.F);
    for (ContentProcessor processor : list) {
      // TODO: 将这两个处理器转成map，key为支持的contentType, value为processor
      processors.put(processor.getSupportedContentType(), processor);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void encode (Object object, Type bodyType, RequestTemplate template) throws EncodeException {
    String contentTypeValue = getContentTypeValue(template.headers());
    val contentType = ContentType.of(contentTypeValue);
    // TODO: 把contentType取到，然后判断processors是否能支持处理这两种，如果不支持，ok，那就用被包装进来的encoder去处理
    if (!processors.containsKey(contentType)) {
      delegate.encode(object, bodyType, template);
      return;
    }

    /* ---------------- 否则它开始去处理 -------------- */

    Map<String, Object> data;
    // TODO: 先看看当前bodyType是否是个map
    if (MAP_STRING_WILDCARD.equals(bodyType)) {
      // TODO: 如果是个map，就进行强转
      data = (Map<String, Object>) object;
      // TODO: 否则判断是否是个 pojo, 如果是，好的，转为一个map，所以我们在用formEncoder的时候，在表单提交时，可以使用java 对象
    } else if (isUserPojo(bodyType)) {
      // TODO: 进行转为map
      data = toMap(object);
    } else {
      // TODO: 否则，就用被包装者去处理编码
      delegate.encode(object, bodyType, template);
      return;
    }

    val charset = getCharset(contentTypeValue);
    // TODO: 从processors中根据指定的contentType拿出来一个处理器去处理
    processors.get(contentType).process(template, charset, data);
  }

  /**
   * Returns {@link ContentProcessor} for specific {@link ContentType}.
   *
   * @param type a type for content processor search.
   *
   * @return {@link ContentProcessor} instance for specified type or null.
   */
  public final ContentProcessor getContentProcessor (ContentType type) {
    return processors.get(type);
  }

  @SuppressWarnings("PMD.AvoidBranchingStatementAsLastInLoop")
  private String getContentTypeValue (Map<String, Collection<String>> headers) {
    for (val entry : headers.entrySet()) {
      if (!entry.getKey().equalsIgnoreCase(CONTENT_TYPE_HEADER)) {
        continue;
      }
      for (val contentTypeValue : entry.getValue()) {
        if (contentTypeValue == null) {
          continue;
        }
        return contentTypeValue;
      }
    }
    return null;
  }

  private Charset getCharset (String contentTypeValue) {
    val matcher = CHARSET_PATTERN.matcher(contentTypeValue);
    return matcher.find()
           ? Charset.forName(matcher.group(1))
           : UTF_8;
  }
}
