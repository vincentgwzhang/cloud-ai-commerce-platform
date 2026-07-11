下面按“**开发时可以直接查参数、复制模板**”的方式整理。示例统一使用：

* `Department` — `Employee`：一对多 / 多对一
* `User` — `UserProfile`：一对一
* `Student` — `Course`：多对多

先记住 JPA 关系映射中最核心的一条：

> **数据库外键在哪张表，通常哪一侧就是 owning side，并负责写 `@JoinColumn`。另一侧使用 `mappedBy`。**

`mappedBy` 填的不是数据库字段名，而是**对方 Java Entity 中属性的名字**。

---

# 一、`@ManyToOne`：最常见的关系

假设：

```text
department
-----------
id
name

employee
--------
id
name
department_id   ← 外键在 employee 表
```

多个 Employee 属于一个 Department。

## 1. 标准双向写法

### Employee：关系拥有方

```java
@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(
        fetch = FetchType.LAZY,
        cascade = {}
    )
    @JoinColumn(
        name = "department_id",
        referencedColumnName = "id",
        nullable = false,
        insertable = true,
        updatable = true,
        foreignKey = @ForeignKey(name = "fk_employee_department")
    )
    private Department department;

    // getters / setters
}
```

### Department：反向方

```java
@Entity
@Table(name = "department")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToMany(
        mappedBy = "department",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<Employee> employees = new ArrayList<>();

    public void addEmployee(Employee employee) {
        employees.add(employee);
        employee.setDepartment(this);
    }

    public void removeEmployee(Employee employee) {
        employees.remove(employee);
        employee.setDepartment(null);
    }

    // getters / setters
}
```

这里：

```java
mappedBy = "department"
```

指的是 `Employee` 中的：

```java
private Department department;
```

不是：

```text
department_id
```

---

## 2. `@ManyToOne` 参数

定义形式可以理解为：

```java
@ManyToOne(
    targetEntity = Department.class,
    cascade = {},
    fetch = FetchType.EAGER,
    optional = true
)
```

### `targetEntity`

```java
@ManyToOne(targetEntity = Department.class)
private Department department;
```

通常不写，因为 JPA 可以从属性类型推断：

```java
private Department department;
```

只有在类型信息不明确、使用接口或特殊泛型结构时才可能显式指定。

---

### `cascade`

```java
@ManyToOne(cascade = CascadeType.PERSIST)
```

可选值：

```java
CascadeType.PERSIST
CascadeType.MERGE
CascadeType.REMOVE
CascadeType.REFRESH
CascadeType.DETACH
CascadeType.ALL
```

含义：

| CascadeType | 父对象操作传播到关联对象                              |
| ----------- | ----------------------------------------- |
| `PERSIST`   | `persist(employee)` 时也 persist department |
| `MERGE`     | merge Employee 时也 merge Department        |
| `REMOVE`    | 删除 Employee 时也删除 Department               |
| `REFRESH`   | refresh Employee 时也 refresh Department    |
| `DETACH`    | detach Employee 时也 detach Department      |
| `ALL`       | 包括以上全部                                    |

对于 `@ManyToOne`，通常**不要使用**：

```java
cascade = CascadeType.ALL
```

尤其不要随便传播 `REMOVE`。

原因是多个 Employee 可能共享同一个 Department：

```text
Employee A ─┐
Employee B ─┼── Department IT
Employee C ─┘
```

删除一个 Employee，不应该删除整个 Department。

常见写法：

```java
@ManyToOne(fetch = FetchType.LAZY)
private Department department;
```

或者最多：

```java
@ManyToOne(
    fetch = FetchType.LAZY,
    cascade = {
        CascadeType.PERSIST,
        CascadeType.MERGE
    }
)
```

---

### `fetch`

规范默认值：

```java
@ManyToOne(fetch = FetchType.EAGER)
```

但实际项目通常主动改成：

```java
@ManyToOne(fetch = FetchType.LAZY)
```

因为 `EAGER` 容易造成：

* 不必要的 SQL
* 大量关联对象加载
* N+1 问题
* 序列化时加载整棵对象图

需要关联数据时，更推荐通过：

* JPQL `join fetch`
* EntityGraph
* DTO projection

显式加载。

---

### `optional`

```java
@ManyToOne(optional = false)
```

表示 Java/JPA 模型中该关联不允许为 `null`。

通常与：

```java
@JoinColumn(nullable = false)
```

一起写：

```java
@ManyToOne(
    fetch = FetchType.LAZY,
    optional = false
)
@JoinColumn(
    name = "department_id",
    nullable = false
)
private Department department;
```

二者语义略有区别：

* `optional = false`：JPA 实体关联不能为空
* `nullable = false`：数据库列不允许 `NULL`

实际开发中建议保持一致。

---

# 二、`@OneToMany`

`@OneToMany` 通常是 `@ManyToOne` 的反向。

## 1. 推荐：双向 `OneToMany + ManyToOne`

```java
@OneToMany(
    mappedBy = "department",
    cascade = CascadeType.ALL,
    orphanRemoval = true,
    fetch = FetchType.LAZY
)
private List<Employee> employees = new ArrayList<>();
```

规范确认：双向 `OneToMany` 中，`mappedBy` 指向拥有关系的另一侧属性；其默认 fetch 策略是 `LAZY`。([jakarta.ee][1])

---

## 2. `@OneToMany` 参数

```java
@OneToMany(
    targetEntity = Employee.class,
    cascade = {},
    fetch = FetchType.LAZY,
    mappedBy = "",
    orphanRemoval = false
)
```

### `mappedBy`

```java
mappedBy = "department"
```

表示：

> 当前 `Department.employees` 不直接维护外键；外键由 `Employee.department` 维护。

因此改变关系时，必须修改 owning side：

```java
employee.setDepartment(department);
```

只执行：

```java
department.getEmployees().add(employee);
```

在内存中看起来有关系，但可能不会正确更新 `employee.department_id`。

所以应写辅助方法：

```java
public void addEmployee(Employee employee) {
    employees.add(employee);
    employee.setDepartment(this);
}
```

---

### `cascade`

父对象完全拥有子对象生命周期时，经常使用：

```java
cascade = CascadeType.ALL
```

例如：

```java
Department department = new Department();
Employee employee = new Employee();

department.addEmployee(employee);

entityManager.persist(department);
```

由于 `CascadeType.PERSIST` 包含在 `ALL` 中，Employee 也会被保存。

但是：

```java
CascadeType.ALL
```

不等于：

```java
orphanRemoval = true
```

二者处理的情况不同。

---

### `orphanRemoval`

```java
@OneToMany(
    mappedBy = "department",
    cascade = CascadeType.ALL,
    orphanRemoval = true
)
private List<Employee> employees;
```

执行：

```java
department.removeEmployee(employee);
```

如果 Employee 从集合中被移除，它会被认为是“孤儿”，JPA 会删除对应 Employee 数据。

大致相当于：

```sql
DELETE FROM employee WHERE id = ?;
```

规范定义中，`orphanRemoval` 表示：当实体从关系中被移除时，对该实体应用删除操作。([jakarta.ee][2])

对比：

```java
cascade = CascadeType.REMOVE
```

处理的是：

```java
entityManager.remove(department);
```

此时连带删除 Employee。

而：

```java
orphanRemoval = true
```

处理的是：

```java
department.getEmployees().remove(employee);
```

即使 Department 自身没有被删除，移出集合的 Employee 也会被删除。

---

## 3. 单向 `@OneToMany`

也可以只让 Department 知道 Employee，而 Employee 不保存 Department 属性。

```java
@Entity
public class Department {

    @Id
    private Long id;

    @OneToMany(
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @JoinColumn(
        name = "department_id",
        nullable = false
    )
    private List<Employee> employees = new ArrayList<>();
}
```

此时：

```java
@Entity
public class Employee {

    @Id
    private Long id;

    // 没有 Department department
}
```

数据库仍然可以是：

```text
employee.department_id
```

但在复杂领域模型里，通常更常见、更自然的是双向：

```java
Department.employees
Employee.department
```

Jakarta Persistence 也允许单向 `OneToMany` 直接使用 `@JoinColumn`，或者通过 `@JoinTable` 建立连接表。([jakarta.ee][3])

---

# 三、`@OneToOne`

假设：

```text
app_user
--------
id
username

user_profile
------------
id
phone
user_id      ← UNIQUE 外键
```

一个 User 对应一个 UserProfile。

---

## 1. 双向共享普通外键

### UserProfile：拥有方

因为 `user_id` 在 `user_profile` 表，所以 UserProfile 是 owning side。

```java
@Entity
@Table(
    name = "user_profile",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_user_profile_user",
            columnNames = "user_id"
        )
    }
)
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String phone;

    @OneToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "user_id",
        referencedColumnName = "id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_profile_user")
    )
    private User user;

    // getters / setters
}
```

### User：反向方

```java
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    @OneToOne(
        mappedBy = "user",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private UserProfile profile;

    public void setProfile(UserProfile profile) {
        this.profile = profile;

        if (profile != null) {
            profile.setUser(this);
        }
    }

    // getters / setters
}
```

这里：

```java
mappedBy = "user"
```

指的是：

```java
UserProfile.user
```

---

## 2. `@OneToOne` 参数

```java
@OneToOne(
    targetEntity = UserProfile.class,
    cascade = {},
    fetch = FetchType.EAGER,
    optional = true,
    mappedBy = "",
    orphanRemoval = false
)
```

与 `OneToMany` 相比，它同时具有：

* `optional`
* `mappedBy`
* `orphanRemoval`

### 常用完整写法

```java
@OneToOne(
    mappedBy = "user",
    cascade = {
        CascadeType.PERSIST,
        CascadeType.MERGE,
        CascadeType.REMOVE
    },
    fetch = FetchType.LAZY,
    optional = true,
    orphanRemoval = true
)
private UserProfile profile;
```

注意：

* `@OneToOne` 规范默认是 `EAGER`
* `mappedBy` 只写在反向方
* `orphanRemoval` 适合 Profile 这种完全依附于 User 的对象
* owning side 通常通过 `unique = true` 保证数据库层面真的是一对一

---

## 3. 共享主键一对一：`@MapsId`

有时 `user_profile.id` 同时也是指向 `app_user.id` 的外键：

```text
app_user
--------
id = 100

user_profile
------------
id = 100   ← 同时是 PK 和 FK
```

写法：

```java
@Entity
@Table(name = "user_profile")
public class UserProfile {

    @Id
    private Long id;

    private String phone;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id",
        referencedColumnName = "id",
        foreignKey = @ForeignKey(name = "fk_profile_user")
    )
    private User user;
}
```

User：

```java
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(
        mappedBy = "user",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private UserProfile profile;
}
```

`@MapsId` 的意思是：

> `UserProfile.user.id` 同时为 `UserProfile.id` 提供值。

---

# 四、`@ManyToMany`

假设：

```text
student
-------
id
name

course
------
id
name

student_course
--------------
student_id
course_id
```

---

## 1. 标准双向写法

### Student：owning side

```java
@Entity
@Table(name = "student")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToMany(
        fetch = FetchType.LAZY,
        cascade = {
            CascadeType.PERSIST,
            CascadeType.MERGE
        }
    )
    @JoinTable(
        name = "student_course",

        joinColumns = {
            @JoinColumn(
                name = "student_id",
                referencedColumnName = "id",
                nullable = false,
                foreignKey = @ForeignKey(name = "fk_student_course_student")
            )
        },

        inverseJoinColumns = {
            @JoinColumn(
                name = "course_id",
                referencedColumnName = "id",
                nullable = false,
                foreignKey = @ForeignKey(name = "fk_student_course_course")
            )
        },

        uniqueConstraints = {
            @UniqueConstraint(
                name = "uk_student_course",
                columnNames = {
                    "student_id",
                    "course_id"
                }
            )
        }
    )
    private Set<Course> courses = new HashSet<>();

    public void addCourse(Course course) {
        courses.add(course);
        course.getStudents().add(this);
    }

    public void removeCourse(Course course) {
        courses.remove(course);
        course.getStudents().remove(this);
    }
}
```

### Course：inverse side

```java
@Entity
@Table(name = "course")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToMany(
        mappedBy = "courses",
        fetch = FetchType.LAZY
    )
    private Set<Student> students = new HashSet<>();
}
```

这里：

```java
mappedBy = "courses"
```

指向：

```java
Student.courses
```

---

## 2. `@ManyToMany` 参数

```java
@ManyToMany(
    targetEntity = Course.class,
    cascade = {},
    fetch = FetchType.LAZY,
    mappedBy = ""
)
```

它没有：

```java
orphanRemoval
```

原因是 Course 通常不是 Student 独占的。

删除 Student 和 Course 的关联，应删除：

```text
student_course
```

中的一行，而不是删除 Course 本身。

---

## 3. Many-to-Many 通常不要用 `REMOVE`

避免：

```java
@ManyToMany(cascade = CascadeType.ALL)
```

因为 `ALL` 包含 `REMOVE`。

假设：

```text
Alice ─┐
       ├── Java Course
Bob ───┘
```

删除 Alice 时，不应该删除 Java Course，否则 Bob 的关联也会受到影响。

更安全：

```java
@ManyToMany(
    cascade = {
        CascadeType.PERSIST,
        CascadeType.MERGE
    }
)
```

甚至不写任何 cascade：

```java
@ManyToMany
```

---

## 4. 实际项目：中间表有额外字段时，不要用 `@ManyToMany`

如果中间表是：

```text
student_course
--------------
student_id
course_id
enrolled_at
status
score
```

这已经不是简单连接表，而是业务实体。

应改成：

```text
Student 1 --- N Enrollment N --- 1 Course
```

```java
@Entity
@Table(name = "enrollment")
public class Enrollment {

    @EmbeddedId
    private EnrollmentId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("studentId")
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("courseId")
    @JoinColumn(name = "course_id")
    private Course course;

    private LocalDateTime enrolledAt;

    @Enumerated(EnumType.STRING)
    private EnrollmentStatus status;

    private Integer score;
}
```

这是企业项目中更常见、更可维护的做法。

---

# 五、`@JoinColumn`

## 常用参数完整版

```java
@JoinColumn(
    name = "department_id",
    referencedColumnName = "id",
    unique = false,
    nullable = false,
    insertable = true,
    updatable = true,
    columnDefinition = "BIGINT",
    table = "employee",
    foreignKey = @ForeignKey(
        name = "fk_employee_department"
    )
)
```

---

## 参数解释

### `name`

本表中的外键列：

```java
name = "department_id"
```

对应：

```text
employee.department_id
```

---

### `referencedColumnName`

被引用表中的列：

```java
referencedColumnName = "id"
```

对应：

```text
department.id
```

通常引用主键时可以省略：

```java
@JoinColumn(name = "department_id")
```

只有引用非主键列时才特别重要：

```java
@JoinColumn(
    name = "department_code",
    referencedColumnName = "code"
)
```

此时被引用列通常应具有唯一约束：

```java
@Column(name = "code", unique = true)
private String code;
```

---

### `nullable`

```java
nullable = false
```

生成数据库 schema 时，该列不允许为 `NULL`。

---

### `unique`

```java
unique = true
```

常用于一对一：

```java
@OneToOne
@JoinColumn(
    name = "user_id",
    unique = true
)
private User user;
```

---

### `insertable`

```java
insertable = false
```

表示 JPA 执行 `INSERT` 时不写这个列。

---

### `updatable`

```java
updatable = false
```

表示 JPA 执行 `UPDATE` 时不更新这个列。

常见场景：同一数据库列被映射两次。

```java
@Column(name = "department_id")
private Long departmentId;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(
    name = "department_id",
    insertable = false,
    updatable = false
)
private Department department;
```

这里：

* `departmentId` 负责写列
* `department` 只负责读取关联

否则 Hibernate 会报类似：

```text
Column 'department_id' is duplicated in mapping
```

---

### `columnDefinition`

```java
columnDefinition = "BIGINT NOT NULL"
```

直接指定数据库 DDL 片段。

通常不建议滥用，因为它会降低数据库可移植性。

---

### `table`

指定外键列属于哪个表，主要用于：

```java
@SecondaryTable
```

等特殊映射。

普通关系几乎不写。

---

### `foreignKey`

```java
foreignKey = @ForeignKey(
    name = "fk_employee_department"
)
```

主要用于 schema 自动生成时控制外键名称。

---

# 六、`@JoinColumns`

当关联需要多个列时使用。

例如 Office 使用复合主键：

```text
office
----------------
country_code  PK
office_code   PK
name

employee
----------------
id
office_country_code
office_code
```

## 1. 复合主键

```java
@Embeddable
public class OfficeId implements Serializable {

    @Column(name = "country_code")
    private String countryCode;

    @Column(name = "office_code")
    private String officeCode;

    // equals / hashCode
}
```

```java
@Entity
@Table(name = "office")
public class Office {

    @EmbeddedId
    private OfficeId id;

    private String name;
}
```

Employee：

```java
@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(
            name = "office_country_code",
            referencedColumnName = "country_code",
            nullable = false
        ),
        @JoinColumn(
            name = "office_code",
            referencedColumnName = "office_code",
            nullable = false
        )
    })
    private Office office;
}
```

记忆方式：

```java
@JoinColumns({
    @JoinColumn(
        name = "本表列1",
        referencedColumnName = "目标表列1"
    ),
    @JoinColumn(
        name = "本表列2",
        referencedColumnName = "目标表列2"
    )
})
```

---

# 七、关系 Annotation 参数速查

## `@OneToOne`

```java
@OneToOne(
    targetEntity = Target.class,
    cascade = {
        CascadeType.PERSIST,
        CascadeType.MERGE
    },
    fetch = FetchType.LAZY,
    optional = false,
    mappedBy = "ownerProperty",
    orphanRemoval = true
)
```

默认值：

```text
cascade       = {}
fetch         = EAGER
optional      = true
mappedBy      = ""
orphanRemoval = false
```

---

## `@ManyToOne`

```java
@ManyToOne(
    targetEntity = Target.class,
    cascade = {},
    fetch = FetchType.LAZY,
    optional = false
)
```

默认值：

```text
cascade  = {}
fetch    = EAGER
optional = true
```

没有：

```text
mappedBy
orphanRemoval
```

因为 `ManyToOne` 本身就是通常的 owning side。

---

## `@OneToMany`

```java
@OneToMany(
    targetEntity = Target.class,
    cascade = CascadeType.ALL,
    fetch = FetchType.LAZY,
    mappedBy = "parent",
    orphanRemoval = true
)
```

默认值：

```text
cascade       = {}
fetch         = LAZY
mappedBy      = ""
orphanRemoval = false
```

---

## `@ManyToMany`

```java
@ManyToMany(
    targetEntity = Target.class,
    cascade = {
        CascadeType.PERSIST,
        CascadeType.MERGE
    },
    fetch = FetchType.LAZY,
    mappedBy = "courses"
)
```

默认值：

```text
cascade  = {}
fetch    = LAZY
mappedBy = ""
```

---

# 八、`@ElementCollection`

`@ElementCollection` 用于集合中的元素不是 Entity，而是：

* 基础类型：`String`、`Integer`、enum
* `@Embeddable` 类型

它声明的是 basic type 或 embeddable class 的集合。([jakarta.ee][4])

---

## 1. `Set<String>`

```java
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ElementCollection(
        fetch = FetchType.LAZY,
        targetClass = String.class
    )
    @CollectionTable(
        name = "user_phone_number",
        joinColumns = {
            @JoinColumn(
                name = "user_id",
                nullable = false,
                foreignKey = @ForeignKey(name = "fk_phone_user")
            )
        }
    )
    @Column(
        name = "phone_number",
        nullable = false,
        length = 30
    )
    private Set<String> phoneNumbers = new HashSet<>();
}
```

数据库：

```text
app_user
--------
id

user_phone_number
-----------------
user_id
phone_number
```

这里：

* `@CollectionTable` 定义集合存放在哪张表
* `joinColumns` 定义集合表如何关联 Entity
* `@Column` 定义集合元素所在的列

---

## 2. Enum 集合

```java
public enum Permission {
    READ,
    WRITE,
    DELETE
}
```

```java
@ElementCollection(fetch = FetchType.LAZY)
@CollectionTable(
    name = "user_permission",
    joinColumns = @JoinColumn(name = "user_id")
)
@Column(name = "permission")
@Enumerated(EnumType.STRING)
private Set<Permission> permissions = new HashSet<>();
```

数据库：

```text
user_permission
---------------
user_id
permission
```

建议：

```java
@Enumerated(EnumType.STRING)
```

不要依赖默认的 ordinal 数字，否则 enum 顺序变化会破坏数据含义。

---

## 3. Embeddable 集合

```java
@Embeddable
public class Address {

    @Column(name = "address_type")
    private String type;

    @Column(name = "street")
    private String street;

    @Column(name = "city")
    private String city;

    @Column(name = "postal_code")
    private String postalCode;
}
```

```java
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "customer_address",
        joinColumns = @JoinColumn(name = "customer_id")
    )
    private List<Address> addresses = new ArrayList<>();
}
```

数据库：

```text
customer_address
----------------
customer_id
address_type
street
city
postal_code
```

---

## 4. 带顺序的 List

```java
@ElementCollection
@CollectionTable(
    name = "article_tag",
    joinColumns = @JoinColumn(name = "article_id")
)
@OrderColumn(name = "tag_order")
@Column(name = "tag")
private List<String> tags = new ArrayList<>();
```

数据库：

```text
article_tag
-----------
article_id
tag_order
tag
```

`@OrderColumn` 保存 List 的实际顺序。

---

## 5. Map

```java
@ElementCollection
@CollectionTable(
    name = "user_setting",
    joinColumns = @JoinColumn(name = "user_id")
)
@MapKeyColumn(name = "setting_key")
@Column(name = "setting_value")
private Map<String, String> settings = new HashMap<>();
```

数据库：

```text
user_setting
------------
user_id
setting_key
setting_value
```

---

## 6. `@ElementCollection` 参数

```java
@ElementCollection(
    targetClass = String.class,
    fetch = FetchType.LAZY
)
```

默认：

```text
targetClass = void.class
fetch       = LAZY
```

通常不需要写 `targetClass`，因为泛型可以推断：

```java
private Set<String> phoneNumbers;
```

### 与 `@OneToMany` 的区别

| `@ElementCollection`         | `@OneToMany`    |
| ---------------------------- | --------------- |
| 元素是 basic 或 embeddable       | 元素必须是 Entity    |
| 元素没有自己的 Entity ID            | 子对象有自己的 `@Id`   |
| 生命周期完全依附拥有者                  | 子 Entity 可以独立存在 |
| 不能单独通过 repository 查询为 Entity | 可以独立查询          |
| 删除父 Entity 时集合值一起消失          | 由 cascade 决定    |

---

# 九、`@Inheritance`

你记得的是：

```java
@Inheritance
@DiscriminatorColumn
@DiscriminatorValue
```

拼写是：

```text
Inheritance
DiscriminatorColumn
DiscriminatorValue
```

JPA Entity 继承有三种策略：

```java
InheritanceType.SINGLE_TABLE
InheritanceType.JOINED
InheritanceType.TABLE_PER_CLASS
```

`@Inheritance` 如果不指定 strategy，默认是：

```java
InheritanceType.SINGLE_TABLE
```

---

# 十、策略一：`SINGLE_TABLE`

所有父类和子类放在同一张表。

假设：

```java
abstract Payment
├── CardPayment
└── BankTransferPayment
```

---

## 1. Entity 定义

### 父类

```java
@Entity
@Table(name = "payment")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(
    name = "payment_type",
    discriminatorType = DiscriminatorType.STRING,
    length = 30
)
public abstract class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private BigDecimal amount;

    private LocalDateTime createdAt;
}
```

### CardPayment

```java
@Entity
@DiscriminatorValue("CARD")
public class CardPayment extends Payment {

    @Column(name = "card_last_four")
    private String cardLastFour;

    @Column(name = "authorization_code")
    private String authorizationCode;
}
```

### BankTransferPayment

```java
@Entity
@DiscriminatorValue("BANK_TRANSFER")
public class BankTransferPayment extends Payment {

    @Column(name = "bank_account")
    private String bankAccount;

    @Column(name = "transfer_reference")
    private String transferReference;
}
```

---

## 2. 数据库表

```text
payment
----------------------------------------------------------------
id
amount
created_at
payment_type
card_last_four
authorization_code
bank_account
transfer_reference
```

数据：

```text
id | amount | payment_type  | card_last_four | bank_account
---+--------+---------------+----------------+-------------
1  | 50.00  | CARD          | 1234           | NULL
2  | 80.00  | BANK_TRANSFER | NULL           | ES123...
```

`payment_type` 就是 discriminator column。

它告诉 Hibernate：

```text
CARD          → CardPayment
BANK_TRANSFER → BankTransferPayment
```

`@DiscriminatorValue` 指定每个具体 Entity 类型写入 discriminator column 的值。([jakarta.ee][5])

---

## 3. `@DiscriminatorColumn` 参数

```java
@DiscriminatorColumn(
    name = "payment_type",
    discriminatorType = DiscriminatorType.STRING,
    columnDefinition = "VARCHAR(30)",
    length = 30
)
```

常用参数：

### `name`

```java
name = "payment_type"
```

默认通常是：

```text
DTYPE
```

---

### `discriminatorType`

可选：

```java
DiscriminatorType.STRING
DiscriminatorType.CHAR
DiscriminatorType.INTEGER
```

官方 API 定义了 `STRING`、`CHAR` 和 `INTEGER` 三种 discriminator 类型。([jakarta.ee][6])

最推荐：

```java
DiscriminatorType.STRING
```

因为数据可读性最好。

---

### `length`

```java
length = 30
```

主要用于 `STRING` 类型。

---

### `columnDefinition`

```java
columnDefinition = "VARCHAR(30)"
```

直接指定数据库 DDL，一般不需要写。

---

## 4. SINGLE_TABLE 优缺点

优点：

* 查询父类型时最快
* 不需要 JOIN
* 多态查询简单
* 表结构数量少

缺点：

* 子类字段都挤在一张表
* 很多列为 `NULL`
* 很难对某个子类专有字段设置 `NOT NULL`
* 子类很多时表会非常宽

这是默认策略，也是最常见的继承策略。

---

# 十一、策略二：`JOINED`

每个类一张表，子类表只保存自己的字段；子类主键同时外键引用父表。

---

## 1. Entity

### 父类

```java
@Entity
@Table(name = "payment")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal amount;

    private LocalDateTime createdAt;
}
```

### CardPayment

```java
@Entity
@Table(name = "card_payment")
@PrimaryKeyJoinColumn(
    name = "payment_id",
    foreignKey = @ForeignKey(name = "fk_card_payment_payment")
)
public class CardPayment extends Payment {

    private String cardLastFour;

    private String authorizationCode;
}
```

### BankTransferPayment

```java
@Entity
@Table(name = "bank_transfer_payment")
@PrimaryKeyJoinColumn(
    name = "payment_id",
    foreignKey = @ForeignKey(name = "fk_bank_payment_payment")
)
public class BankTransferPayment extends Payment {

    private String bankAccount;

    private String transferReference;
}
```

---

## 2. 数据库

```text
payment
----------------
id
amount
created_at
```

```text
card_payment
-------------------
payment_id  PK + FK
card_last_four
authorization_code
```

```text
bank_transfer_payment
---------------------
payment_id  PK + FK
bank_account
transfer_reference
```

加载 CardPayment 时大致需要：

```sql
SELECT ...
FROM payment p
JOIN card_payment c
    ON c.payment_id = p.id
WHERE p.id = ?;
```

---

## 3. 是否需要 discriminator？

规范允许 `@DiscriminatorColumn` 用于：

* `SINGLE_TABLE`
* `JOINED`

并且应声明在继承层次的根 Entity 上。([jakarta.ee][7])

因此可以写：

```java
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(
    name = "payment_type",
    discriminatorType = DiscriminatorType.STRING
)
public abstract class Payment {
}
```

子类：

```java
@Entity
@DiscriminatorValue("CARD")
public class CardPayment extends Payment {
}
```

但对于 `JOINED`，数据库通常也可以通过子类表中是否存在对应行来识别类型。因此 discriminator 在 `JOINED` 中不是每个项目都会显式使用，具体行为还要留意 provider 和 schema 设计。

---

## 4. JOINED 优缺点

优点：

* 数据库结构规范化
* 没有大量子类字段为 NULL
* 子类专有字段可以有严格约束
* 表结构表达领域模型较清晰

缺点：

* 查询子类需要 JOIN
* 查询整个父类层次可能 JOIN 多张表
* 子类越多，查询越复杂
* 批量查询性能可能不如 SINGLE_TABLE

---

# 十二、策略三：`TABLE_PER_CLASS`

每个具体子类各自拥有一张完整表，包括继承来的字段。

---

## 1. Entity

```java
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private BigDecimal amount;

    private LocalDateTime createdAt;
}
```

```java
@Entity
@Table(name = "card_payment")
public class CardPayment extends Payment {

    private String cardLastFour;

    private String authorizationCode;
}
```

```java
@Entity
@Table(name = "bank_transfer_payment")
public class BankTransferPayment extends Payment {

    private String bankAccount;

    private String transferReference;
}
```

---

## 2. 数据库

```text
card_payment
-------------------
id
amount
created_at
card_last_four
authorization_code
```

```text
bank_transfer_payment
---------------------
id
amount
created_at
bank_account
transfer_reference
```

没有统一的：

```text
payment
```

表。

`TABLE_PER_CLASS` 的每个 concrete Entity 都有自己的完整表，不需要 discriminator column。([jakarta.ee][8])

---

## 3. 多态查询

```java
select p from Payment p
```

数据库层面通常需要类似：

```sql
SELECT ... FROM card_payment
UNION ALL
SELECT ... FROM bank_transfer_payment
```

因此：

* 查询单个具体子类比较直接
* 查询父类型可能产生 `UNION`
* 公共字段会在多张表中重复

---

## 4. TABLE_PER_CLASS 优缺点

优点：

* 查询具体子类不需要 JOIN
* 每张表完整、自包含
* 子类表不会出现其他子类的 NULL 字段

缺点：

* 父类字段重复
* 多态查询需要 `UNION`
* schema 修改时多张表都要调整
* ID 生成策略更受限制
* 实际项目相对少见

---

# 十三、三种继承策略对比

| 策略                | 表结构        |    子类查询 |   父类多态查询 | NULL 列 | Discriminator |
| ----------------- | ---------- | ------: | -------: | -----: | ------------- |
| `SINGLE_TABLE`    | 整个继承树一张表   |       快 |        快 |      多 | 必要            |
| `JOINED`          | 父类、子类分别一张表 | 需要 JOIN |  多个 JOIN |      少 | 可使用           |
| `TABLE_PER_CLASS` | 每个具体类完整一张表 |       快 | 通常 UNION |      少 | 不需要           |

实际选择：

```text
优先性能、层次不复杂
→ SINGLE_TABLE

优先数据库规范化、子类字段差异明显
→ JOINED

很少查询父类型、每个子类近似独立
→ TABLE_PER_CLASS
```

---

# 十四、`@MappedSuperclass` 与 `@Inheritance` 的区别

这个很容易混淆。

## `@MappedSuperclass`

```java
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
```

```java
@Entity
public class User extends BaseEntity {
}
```

```java
@Entity
public class Product extends BaseEntity {
}
```

特点：

* `BaseEntity` 不是 Entity
* 没有 `base_entity` 表
* 不能执行：

```java
select b from BaseEntity b
```

* 只是让子 Entity 继承字段映射

Jakarta Persistence 明确定义：mapped superclass 本身不是 Entity，也不会映射为独立数据库表。([jakarta.ee][9])

---

## `@Inheritance`

```java
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class Payment {
}
```

特点：

* 父类本身是 Entity
* 可以做多态查询：

```java
select p from Payment p
```

* 子类是同一个业务继承层次的一部分
* 有 `SINGLE_TABLE`、`JOINED`、`TABLE_PER_CLASS` 三种策略

---

# 十五、最终记忆模板

## 多对一

```java
@ManyToOne(
    fetch = FetchType.LAZY,
    optional = false
)
@JoinColumn(
    name = "parent_id",
    referencedColumnName = "id",
    nullable = false,
    foreignKey = @ForeignKey(name = "fk_child_parent")
)
private Parent parent;
```

## 一对多

```java
@OneToMany(
    mappedBy = "parent",
    cascade = CascadeType.ALL,
    orphanRemoval = true,
    fetch = FetchType.LAZY
)
private List<Child> children = new ArrayList<>();
```

## 一对一 owning side

```java
@OneToOne(
    fetch = FetchType.LAZY,
    optional = false
)
@JoinColumn(
    name = "target_id",
    nullable = false,
    unique = true
)
private Target target;
```

## 一对一 inverse side

```java
@OneToOne(
    mappedBy = "owner",
    cascade = CascadeType.ALL,
    orphanRemoval = true,
    fetch = FetchType.LAZY
)
private OwnerDetail detail;
```

## 多对多 owning side

```java
@ManyToMany(
    fetch = FetchType.LAZY,
    cascade = {
        CascadeType.PERSIST,
        CascadeType.MERGE
    }
)
@JoinTable(
    name = "a_b",
    joinColumns = @JoinColumn(name = "a_id"),
    inverseJoinColumns = @JoinColumn(name = "b_id")
)
private Set<B> items = new HashSet<>();
```

## ElementCollection

```java
@ElementCollection(fetch = FetchType.LAZY)
@CollectionTable(
    name = "owner_value",
    joinColumns = @JoinColumn(name = "owner_id")
)
@Column(name = "value")
private Set<String> values = new HashSet<>();
```

## Inheritance

```java
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(
    name = "entity_type",
    discriminatorType = DiscriminatorType.STRING,
    length = 30
)
public abstract class BaseEntity {
}
```

```java
@Entity
@DiscriminatorValue("TYPE_A")
public class TypeA extends BaseEntity {
}
```

[1]: https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/onetomany?utm_source=chatgpt.com "OneToMany (Jakarta Persistence API documentation)"
[2]: https://jakarta.ee/specifications/persistence/3.1/apidocs/index-all?utm_source=chatgpt.com "Index (Jakarta Persistence API documentation)"
[3]: https://jakarta.ee/specifications/persistence/4.0/apidocs/jakarta.persistence/jakarta/persistence/onetomany?utm_source=chatgpt.com "OneToMany (Jakarta Persistence API documentation)"
[4]: https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/package-summary?utm_source=chatgpt.com "Package jakarta.persistence"
[5]: https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/discriminatorvalue?utm_source=chatgpt.com "DiscriminatorValue (Jakarta Persistence API documentation)"
[6]: https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/discriminatortype?utm_source=chatgpt.com "DiscriminatorType (Jakarta Persistence API documentation)"
[7]: https://jakarta.ee/specifications/persistence/2.2/apidocs/javax/persistence/discriminatorcolumn?utm_source=chatgpt.com "Annotation Type DiscriminatorColumn"
[8]: https://jakarta.ee/specifications/persistence/4.0/apidocs/jakarta.persistence/jakarta/persistence/inheritancetype?utm_source=chatgpt.com "InheritanceType (Jakarta Persistence API documentation)"
[9]: https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/mappedsuperclass?utm_source=chatgpt.com "MappedSuperclass (Jakarta Persistence API documentation)"
