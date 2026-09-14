-- =====================================================================
-- 意图即服务（Intent as a Service）建表与菜单脚本 · MySQL
--
-- 说明：
--   1. 三张表由 ruoyi-common-intent 平台模块使用，与具体业务无关；
--   2. 意图规范与执行器档案支持"零建表"运行吗？——不支持。
--      与本框架既有的仓储习惯不同，意图规范必须落库：目录与执行引擎以 DB 为单一事实来源，
--      后台增删改才能即时生效。所以这三张表是必建的。
--   3. 菜单脚本会创建「意图中心」目录与三个页面，并给测试角色授权；
--      超管（superadmin）无需授权即可见。
--
-- 执行方式：在业务库（默认 ry-vue）中执行本脚本，然后重启应用。
-- =====================================================================

-- ----------------------------
-- 1. 意图规范表
-- ----------------------------
drop table if exists intent_spec;
create table intent_spec
(
    id          bigint(20)   not null comment '主键',
    intent_id   varchar(128) not null comment '意图编号（系统.域.动作，如 system.user.risk-scan）',
    name        varchar(128) not null comment '意图名称',
    description varchar(512)  default '' comment '意图描述',
    scope       varchar(16)   default 'LOCAL' comment '执行范围（LOCAL/REMOTE/COMPOSITE）',
    version     int(4)        default 1 comment '契约版本',
    executor    varchar(64)   default null comment '执行器标识（空 = 内置 builtin-agent 推理循环）',
    source      varchar(16)   default 'builtin' comment '来源（builtin=classpath 种子，custom=后台创建）',
    spec_yaml   longtext comment '意图规范原文（YAML，后台编辑的就是这一份）',
    remark      varchar(500)  default '' comment '备注',
    create_dept bigint(20)    default null comment '创建部门',
    create_by   bigint(20)    default null comment '创建者',
    create_time datetime comment '创建时间',
    update_by   bigint(20)    default null comment '更新者',
    update_time datetime comment '更新时间',
    del_flag    char(1)       default '0' comment '删除标志（0代表存在 1代表删除）',
    primary key (id),
    unique key uk_intent_spec_intent_id (intent_id)
) engine = innodb comment = '意图规范表（意图定义的单一事实来源）';

-- ----------------------------
-- 2. 意图运营配置表
-- ----------------------------
drop table if exists intent_config;
create table intent_config
(
    id          bigint(20)   not null comment '主键',
    intent_id   varchar(128) not null comment '意图编号',
    enabled     tinyint(1)    default 1 comment '是否上架（0=下架，目录与执行都拒绝）',
    roles       varchar(512)  default '["*"]' comment '可见角色编码（JSON 数组；["*"] 或空 = 不限制）',
    executor    varchar(64)   default null comment '指定执行器标识（空 = 用规范声明或内置执行器）',
    remark      varchar(500)  default '' comment '备注',
    create_dept bigint(20)    default null comment '创建部门',
    create_by   bigint(20)    default null comment '创建者',
    create_time datetime comment '创建时间',
    update_by   bigint(20)    default null comment '更新者',
    update_time datetime comment '更新时间',
    del_flag    char(1)       default '0' comment '删除标志（0代表存在 1代表删除）',
    primary key (id),
    unique key uk_intent_config_intent_id (intent_id)
) engine = innodb comment = '意图运营配置表（上架开关 / 可见角色 / 执行器）';

-- ----------------------------
-- 3. 执行器档案表
-- ----------------------------
drop table if exists intent_executor;
create table intent_executor
(
    id           bigint(20)  not null comment '主键',
    executor_id  varchar(64) not null comment '执行器标识（builtin-agent 为保留 id，不可占用）',
    type         varchar(16)  default 'agent' comment '类型（agent/skill）',
    name         varchar(128) default '' comment '名称',
    source       varchar(16)  default 'builtin' comment '来源（builtin/custom）',
    profile_yaml longtext comment '执行器档案原文（YAML）',
    remark       varchar(500) default '' comment '备注',
    create_dept  bigint(20)   default null comment '创建部门',
    create_by    bigint(20)   default null comment '创建者',
    create_time  datetime comment '创建时间',
    update_by    bigint(20)   default null comment '更新者',
    update_time  datetime comment '更新时间',
    del_flag     char(1)      default '0' comment '删除标志（0代表存在 1代表删除）',
    primary key (id),
    unique key uk_intent_executor_executor_id (executor_id)
) engine = innodb comment = '执行器档案表（声明式执行器：agent / skill / flow）';

-- =====================================================================
-- 4. 菜单与权限
--    固定审计三件套：create_dept=1761000000000000103（研发部门）
--                   create_by  =1761100000000000001（admin）
--                   create_time=sysdate()
-- =====================================================================

-- 目录：意图中心
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type,
                      visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time,
                      remark)
values (1763000000000000001, '意图中心', 0, 5, 'intent', null, 'N', 'Y', 'M', '0', '0', '', 'robot',
        1761000000000000103, 1761100000000000001, sysdate(), null, null, '意图即服务目录');

-- 菜单：意图调试台（全量目录 + debug 面板，所有角色都该有）
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type,
                      visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time,
                      remark)
values (1763000000000000100, '意图调试台', 1763000000000000001, 1, 'center', 'intent/center/index', 'N', 'Y', 'C',
        '0', '0', 'intent:center:list', 'tool', 1761000000000000103, 1761100000000000001, sysdate(), null, null,
        '意图调试台菜单');

-- 菜单：意图管理（上架 / 角色 / 执行器）
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type,
                      visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time,
                      remark)
values (1763000000000000101, '意图管理', 1763000000000000001, 2, 'config', 'intent/config/index', 'N', 'Y', 'C', '0',
        '0', 'intent:config:query', 'edit', 1761000000000000103, 1761100000000000001, sysdate(), null, null,
        '意图管理菜单');

-- 菜单：执行器档案
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type,
                      visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time,
                      remark)
values (1763000000000000102, '执行器档案', 1763000000000000001, 3, 'executor', 'intent/executor/index', 'N', 'Y', 'C',
        '0', '0', 'intent:executor:query', 'component', 1761000000000000103, 1761100000000000001, sysdate(), null,
        null, '执行器档案菜单');

-- 按钮：意图配置
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type,
                      visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time,
                      remark)
values (1763000000000001001, '意图配置修改', 1763000000000000101, 1, '#', '', 'N', 'Y', 'F', '0', '0',
        'intent:config:update', '#', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- 按钮：意图规范
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type,
                      visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time,
                      remark)
values (1763000000000001002, '规范查询', 1763000000000000101, 2, '#', '', 'N', 'Y', 'F', '0', '0', 'intent:spec:query',
        '#', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type,
                      visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time,
                      remark)
values (1763000000000001003, '规范新增', 1763000000000000101, 3, '#', '', 'N', 'Y', 'F', '0', '0', 'intent:spec:add',
        '#', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type,
                      visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time,
                      remark)
values (1763000000000001004, '规范修改', 1763000000000000101, 4, '#', '', 'N', 'Y', 'F', '0', '0', 'intent:spec:edit',
        '#', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type,
                      visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time,
                      remark)
values (1763000000000001005, '规范删除', 1763000000000000101, 5, '#', '', 'N', 'Y', 'F', '0', '0', 'intent:spec:remove',
        '#', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- 按钮：执行器档案
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type,
                      visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time,
                      remark)
values (1763000000000001006, '执行器新增', 1763000000000000102, 1, '#', '', 'N', 'Y', 'F', '0', '0',
        'intent:executor:add', '#', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type,
                      visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time,
                      remark)
values (1763000000000001007, '执行器修改', 1763000000000000102, 2, '#', '', 'N', 'Y', 'F', '0', '0',
        'intent:executor:edit', '#', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type,
                      visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time,
                      remark)
values (1763000000000001008, '执行器删除', 1763000000000000102, 3, '#', '', 'N', 'Y', 'F', '0', '0',
        'intent:executor:remove', '#', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- =====================================================================
-- 5. 角色授权
--    超管（superadmin）在代码里被硬编码放行，无需 sys_role_menu 记录；
--    这里给种子里的测试角色 test1（1761300000000000003）授权，
--    其余角色请到「系统管理 → 角色管理 → 分配菜单」勾选，或按同样格式追加。
-- =====================================================================
insert into sys_role_menu values (1761300000000000003, 1763000000000000001);
insert into sys_role_menu values (1761300000000000003, 1763000000000000100);
insert into sys_role_menu values (1761300000000000003, 1763000000000000101);
insert into sys_role_menu values (1761300000000000003, 1763000000000000102);
insert into sys_role_menu values (1761300000000000003, 1763000000000001001);
insert into sys_role_menu values (1761300000000000003, 1763000000000001002);
insert into sys_role_menu values (1761300000000000003, 1763000000000001003);
insert into sys_role_menu values (1761300000000000003, 1763000000000001004);
insert into sys_role_menu values (1761300000000000003, 1763000000000001005);
insert into sys_role_menu values (1761300000000000003, 1763000000000001006);
insert into sys_role_menu values (1761300000000000003, 1763000000000001007);
insert into sys_role_menu values (1761300000000000003, 1763000000000001008);
