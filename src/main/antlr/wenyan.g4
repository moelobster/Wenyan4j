grammar wenyan;

program                     : statement* EOF ;
statement                   : declare_statement
                            | define_statement
                            | print_statement
                            | for_statement
                            | function_statement
                            | wait_statement
                            | if_statement
                            | return_statement
                            | math_statement
                            | assign_statement
                            | import_statement
                            | object_statement
                            | reference_statement
                            | array_statement
                            | class_statement
                            | interface_define
                            | super_constructor_call
                            | flush_statement
                            | BREAK
                            | comment;
reference_statement         : '夫' reference_base ('之' (STRING_LITERAL|INT_NUM|'其餘'|IDENTIFIER|'長'))? name_single_statement? ;
reference_base              : data | '其' | '己' | '父' ;
array_statement             : array_cat_statement|array_push_statement ;
array_cat_statement         : '銜' (IDENTIFIER|'其') (PREPOSITION_RIGHT IDENTIFIER)+ name_single_statement?;
array_push_statement        : '充' (IDENTIFIER|'其') (PREPOSITION_RIGHT data)+ name_single_statement?;
function_statement          : function_define_statement|(function_call_statement (name_single_statement)?) ;
function_call_statement     : instance_method_call
                            | function_pre_call
                            | function_post_call ;
function_pre_call           : ('施' IDENTIFIER (preposition data)*)
                            | ('施其' (preposition data)*) ;
wait_statement              : (('待之以' data TIME_UNIT?)
                            | ('待施' IDENTIFIER (preposition data)*)
                            | ('待施其' (preposition data)*))
                            (wait_crash_branch FOR_IF_END)? (name_single_statement)? ;
wait_crash_branch           : ('若非' statement+) ;
function_post_call          : ('取' INT_NUM '以施' IDENTIFIER)+ ;
function_define_statement   : '吾有' INT_NUM '術' name_single_statement ('欲行是術' '必先得' (INT_NUM param_type ('曰' IDENTIFIER)+)+)? ('是術曰'|'乃行是術曰') statement* '是謂' IDENTIFIER '之術也' ;
if_statement                : IF if_expression '者' statement+ (ELSE statement+)? FOR_IF_END ;
if_expression               : unary_if_expression|binary_if_expression ;
param_type                  : TYPE | '術' ;
unary_if_expression         : data
                            | (IDENTIFIER '之'('長'|STRING_LITERAL|IDENTIFIER))
                            | ('其' '之' ('長'|STRING_LITERAL|IDENTIFIER))
                            | '其' ;
binary_if_expression        : unary_if_expression IF_LOGIC_OP unary_if_expression ;
declare_statement           : ('吾有'|'今有') INT_NUM TYPE ('曰' data)*;
define_statement            : (declare_statement name_multi_statement)|init_define_statement ;
name_multi_statement        : '名之' ('曰' IDENTIFIER)+ ;
name_single_statement       : '名之' ('曰' IDENTIFIER) ;
init_define_statement       : '有' TYPE data (name_single_statement)? ;
for_statement               : for_arr_statement
                            | for_enum_statement
                            | for_while_statement ;
for_arr_statement           : FOR_START_ARR   IDENTIFIER            FOR_MID_ARR  IDENTIFIER statement* FOR_IF_END ;
for_enum_statement          : FOR_START_ENUM  (INT_NUM|IDENTIFIER)  FOR_MID_ENUM statement* FOR_IF_END ;
for_while_statement         : FOR_START_WHILE statement*            FOR_IF_END ;
math_statement              : (arith_math_statement|boolean_algebra_statement|mod_math_statement) (name_multi_statement)? ;
arith_math_statement        : arith_binary_math|arith_unary_math ;
arith_binary_math           : ARITH_BINARY_OP (data|'其') preposition (data|'其') ;
arith_unary_math            : UNARY_OP (IDENTIFIER|'其') ;
mod_math_statement          : '除' (INT_NUM|FLOAT_NUM|IDENTIFIER|'其') preposition (INT_NUM|FLOAT_NUM|IDENTIFIER|'其') POST_MOD_MATH_OP? ;
boolean_algebra_statement   : '夫' IDENTIFIER IDENTIFIER LOGIC_BINARY_OP ;
assign_statement            : '昔之' assign_target ('之' (INT_NUM|STRING_LITERAL|IDENTIFIER|'長'))? '者' (('今' ((data ('之' (INT_NUM|STRING_LITERAL|IDENTIFIER|'長'))?)|('其' ('之' (INT_NUM|STRING_LITERAL|IDENTIFIER|'長'))?)) '是矣')|'今不復存矣') ;
assign_target               : IDENTIFIER | '己' | '父' ;
return_statement            : ('乃得' (data|'其'))|'乃歸空無'|'乃得矣'|('即拒' (data|'其')) ;
import_statement            : '吾嘗觀' STRING_LITERAL '之書' ('方悟' IDENTIFIER+ '之義')? ;
object_statement            : '吾有' INT_NUM '物' name_multi_statement (object_define_statement)? ;
object_define_statement     : '其物如是' ('物之' STRING_LITERAL '者' TYPE '曰' data)+ '是謂' IDENTIFIER '之物也' ;
data                        : STRING_LITERAL|BOOL_VALUE|IDENTIFIER|INT_NUM|FLOAT_NUM ;

// ---------- 面向对象语法 ----------

class_statement             : class_define | class_instantiate ;

class_define                : '吾有' INT_NUM CLASS_MODIFIER '曰' IDENTIFIER
                              ('承' IDENTIFIER)?
                              ('守' IDENTIFIER+)?
                              '其族如是'
                              class_member*
                              '是謂' IDENTIFIER '之族也' ;

class_member                : property_define | method_define | constructor_define ;

property_define             : property_prefix IDENTIFIER '者' TYPE FOR_IF_END ('曰' data)? ;
property_prefix             : '公之' '恆性'? | '私之' '恆性'? | '密之' '恆性'? | '其恆性' | '其' ;

method_define               : method_prefix IDENTIFIER
                              (method_params)?
                              (FOR_IF_END | ('乃行是術曰' statement* '是謂' IDENTIFIER '之術也')) ;
method_prefix               : '其公術' | '其私術' | '其密術' | '其虚術' ;
method_params               : '欲行是術' '必先得' (INT_NUM param_type ('曰' IDENTIFIER)+)+ ;

constructor_define          : '其初術'
                              ('欲行是術' '必先得' (INT_NUM param_type ('曰' IDENTIFIER)+)+)?
                              '乃行是術曰'
                              statement*
                              '是謂之初術也' ;

class_instantiate           : '生一' IDENTIFIER ('曰' IDENTIFIER)? ('与' data)* ;

interface_define            : '吾有' INT_NUM '约' '曰' IDENTIFIER
                              '其约如是'
                              interface_method*
                              '是謂' IDENTIFIER '之约也' ;

interface_method            : '其公術' IDENTIFIER
                              ('欲行是術' '必先得' (INT_NUM param_type ('曰' IDENTIFIER)+)+)?
                              FOR_IF_END ;

instance_method_call        : '施' (IDENTIFIER | '其' | '己' | '父') '之' IDENTIFIER ('与' data)* ;

super_constructor_call      : '施父之初' ('与' data)* ;

// ---------- 词法 ----------

STRING_LITERAL              : '「「' ( ~('」') )* '」」'
                            | '『' ( ~('』') )* '』' ;
IDENTIFIER                  : '「' ( ~('」') )+ '」' ;
ARITH_BINARY_OP             : '加'|'減'|'减'|'乘' ;
LOGIC_BINARY_OP             : '中有陽乎'|'中無陰乎' ;
POST_MOD_MATH_OP            : '所餘幾何' ;
UNARY_OP                    : '變' ;
preposition                 : PREPOSITION_LEFT|PREPOSITION_RIGHT ;
PREPOSITION_LEFT            : '於' ;
PREPOSITION_RIGHT           : '以' ;
IF                          : '若' ;
ELSE                        : '若非' ;
IF_LOGIC_OP                 : '等於'|'不等於'|'不大於'|'不小於'|'大於'|'小於' ;
FOR_START_ARR               : '凡' ;
FOR_START_ENUM              : '為是' ;
FOR_START_WHILE             : '恆為是' ;
FOR_MID_ARR                 : '中之' ;
FOR_MID_ENUM                : '遍' ;
FOR_IF_END                  : '云云'|'也' ;
FLOAT_NUM                   : INT_NUM '又' (INT_NUM FLOAT_NUM_KEYWORDS)+ ;
FLOAT_NUM_KEYWORDS          : '分'|'釐'|'毫'|'絲'|'忽'|'微'|'塵'|'埃'|'渺'|'漠' ;
TIME_UNIT                    : '秒'|'分'|'時'|'日'|'月'|'年' ;
INT_NUM                     : INT_NUM_KEYWORDS+ ;
INT_NUM_KEYWORDS            : '零'|'〇'|'一'|'二'|'三'|'四'|'五'|'六'|'七'|'八'|'九'|'十'|'百'|'千'|'萬'|'亿'|'億'|'兆'|'京'|'垓'|'秭'|'穣'|'溝'|'澗'|'正'|'載'|'極' ;
TYPE                        : '數'|'列'|'言'|'爻' ;
BOOL_VALUE                  : '陰'|'陽' ;
CLASS_MODIFIER              : '虚族' | '终族' | '族' ;
print_statement             : '書之' ;
WS                          : ([ \t\r\n]|'。'|'、'|'，')+ -> skip ;
comment                     : ('注曰'|'疏曰'|'批曰') STRING_LITERAL ;
flush_statement             : '噫' ;
BREAK                       : '乃止' ;
