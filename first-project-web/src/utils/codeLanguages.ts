// Curated list of mainstream programming languages for the code-snippet editor.
// The `language` field stored on a snippet is a human-facing label (e.g. "TypeScript");
// highlighting and formatting resolve that label/alias to one of these entries.

export interface CodeLanguage {
  id: string
  label: string
  aliases: string[]
  kind: 'source' | 'markup' | 'css' | 'json' | 'text' | 'sql'
  lineComment: string[]
  blockComment: [string, string] | null
  keywords: string[]
}

const kw = (...words: string[]) => words

const JS_KEYWORDS = kw(
  'as', 'async', 'await', 'break', 'case', 'catch', 'class', 'const', 'continue', 'debugger',
  'default', 'delete', 'do', 'else', 'export', 'extends', 'finally', 'for', 'from', 'function',
  'get', 'if', 'import', 'in', 'instanceof', 'let', 'new', 'of', 'return', 'set', 'static',
  'super', 'switch', 'this', 'throw', 'try', 'typeof', 'var', 'void', 'while', 'with', 'yield',
)

const TS_KEYWORDS = kw(
  ...JS_KEYWORDS,
  'abstract', 'any', 'as', 'asserts', 'bigint', 'boolean', 'declare', 'enum', 'implements',
  'infer', 'interface', 'is', 'keyof', 'namespace', 'never', 'number', 'object', 'private',
  'protected', 'public', 'readonly', 'string', 'symbol', 'type', 'undefined', 'unknown',
)

const PYTHON_KEYWORDS = kw(
  'and', 'as', 'assert', 'async', 'await', 'break', 'class', 'continue', 'def', 'del', 'elif',
  'else', 'except', 'finally', 'for', 'from', 'global', 'if', 'import', 'in', 'is', 'lambda',
  'nonlocal', 'not', 'or', 'pass', 'raise', 'return', 'try', 'while', 'with', 'yield', 'True',
  'False', 'None',
)

const JAVA_KEYWORDS = kw(
  'abstract', 'assert', 'boolean', 'break', 'byte', 'case', 'catch', 'char', 'class', 'const',
  'continue', 'default', 'do', 'double', 'else', 'enum', 'extends', 'final', 'finally', 'float',
  'for', 'goto', 'if', 'implements', 'import', 'instanceof', 'int', 'interface', 'long', 'native',
  'new', 'package', 'private', 'protected', 'public', 'return', 'short', 'static', 'strictfp',
  'super', 'switch', 'synchronized', 'this', 'throw', 'throws', 'transient', 'try', 'void',
  'volatile', 'while', 'var', 'record', 'sealed', 'permits', 'yield',
)

const C_KEYWORDS = kw(
  'auto', 'break', 'case', 'char', 'const', 'continue', 'default', 'do', 'double', 'else', 'enum',
  'extern', 'float', 'for', 'goto', 'if', 'inline', 'int', 'long', 'register', 'restrict',
  'return', 'short', 'signed', 'sizeof', 'static', 'struct', 'switch', 'typedef', 'union',
  'unsigned', 'void', 'volatile', 'while',
)

const CPP_KEYWORDS = kw(
  ...C_KEYWORDS, 'alignas', 'alignof', 'bool', 'catch', 'class', 'concept', 'constexpr',
  'const_cast', 'decltype', 'delete', 'dynamic_cast', 'explicit', 'export', 'false', 'friend',
  'mutable', 'namespace', 'new', 'noexcept', 'nullptr', 'operator', 'override', 'private',
  'protected', 'public', 'reinterpret_cast', 'static_assert', 'static_cast', 'template', 'this',
  'throw', 'true', 'try', 'typeid', 'typename', 'using', 'virtual',
)

const CSHARP_KEYWORDS = kw(
  'abstract', 'as', 'base', 'bool', 'break', 'byte', 'case', 'catch', 'char', 'checked', 'class',
  'const', 'continue', 'decimal', 'default', 'delegate', 'do', 'double', 'else', 'enum', 'event',
  'explicit', 'extern', 'false', 'finally', 'fixed', 'float', 'for', 'foreach', 'goto', 'if',
  'implicit', 'in', 'int', 'interface', 'internal', 'is', 'lock', 'long', 'namespace', 'new',
  'null', 'object', 'operator', 'out', 'override', 'params', 'private', 'protected', 'public',
  'readonly', 'ref', 'return', 'sbyte', 'sealed', 'short', 'sizeof', 'stackalloc', 'static',
  'string', 'struct', 'switch', 'this', 'throw', 'true', 'try', 'typeof', 'uint', 'ulong',
  'unchecked', 'unsafe', 'ushort', 'using', 'virtual', 'void', 'volatile', 'while', 'var',
  'async', 'await', 'record', 'init', 'required',
)

const GO_KEYWORDS = kw(
  'break', 'case', 'chan', 'const', 'continue', 'default', 'defer', 'else', 'fallthrough', 'for',
  'func', 'go', 'goto', 'if', 'import', 'interface', 'map', 'package', 'range', 'return',
  'select', 'struct', 'switch', 'type', 'var',
)

const RUST_KEYWORDS = kw(
  'as', 'async', 'await', 'break', 'const', 'continue', 'crate', 'dyn', 'else', 'enum', 'extern',
  'false', 'fn', 'for', 'if', 'impl', 'in', 'let', 'loop', 'match', 'mod', 'move', 'mut', 'pub',
  'ref', 'return', 'self', 'Self', 'static', 'struct', 'super', 'trait', 'true', 'type', 'unsafe',
  'use', 'where', 'while',
)

const PHP_KEYWORDS = kw(
  'abstract', 'and', 'array', 'as', 'break', 'callable', 'case', 'catch', 'class', 'clone',
  'const', 'continue', 'declare', 'default', 'do', 'echo', 'else', 'elseif', 'empty', 'enddeclare',
  'endfor', 'endforeach', 'endif', 'endswitch', 'endwhile', 'enum', 'extends', 'final', 'finally',
  'fn', 'for', 'foreach', 'function', 'global', 'goto', 'if', 'implements', 'include',
  'include_once', 'instanceof', 'insteadof', 'interface', 'isset', 'list', 'match', 'namespace',
  'new', 'or', 'print', 'private', 'protected', 'public', 'readonly', 'require', 'require_once',
  'return', 'static', 'switch', 'throw', 'trait', 'try', 'unset', 'use', 'var', 'while', 'xor',
  'yield', 'true', 'false', 'null',
)

const RUBY_KEYWORDS = kw(
  'alias', 'and', 'begin', 'break', 'case', 'class', 'def', 'do', 'else', 'elsif', 'end',
  'ensure', 'false', 'for', 'if', 'in', 'module', 'next', 'nil', 'not', 'or', 'redo', 'rescue',
  'retry', 'return', 'self', 'super', 'then', 'true', 'undef', 'unless', 'until', 'when',
  'while', 'yield',
)

const DART_KEYWORDS = kw(
  'abstract', 'as', 'assert', 'async', 'await', 'break', 'case', 'catch', 'class', 'const',
  'continue', 'covariant', 'default', 'deferred', 'do', 'dynamic', 'else', 'enum', 'export',
  'extends', 'extension', 'external', 'factory', 'false', 'final', 'finally', 'for', 'get', 'hide',
  'if', 'implements', 'import', 'in', 'interface', 'is', 'late', 'library', 'mixin', 'new',
  'null', 'on', 'operator', 'part', 'required', 'rethrow', 'return', 'set', 'show', 'static',
  'super', 'switch', 'sync', 'this', 'throw', 'true', 'try', 'typedef', 'var', 'void', 'while',
  'with', 'yield',
)

const SQL_KEYWORDS = kw(
  'add', 'all', 'alter', 'and', 'any', 'as', 'asc', 'begin', 'between', 'by', 'case', 'check',
  'column', 'commit', 'constraint', 'create', 'cross', 'current_date', 'current_timestamp',
  'database', 'default', 'delete', 'desc', 'distinct', 'drop', 'else', 'end', 'except', 'exists',
  'false', 'foreign', 'from', 'full', 'group', 'having', 'if', 'in', 'index', 'inner', 'insert',
  'intersect', 'into', 'is', 'join', 'key', 'left', 'like', 'limit', 'not', 'null', 'offset',
  'on', 'or', 'order', 'outer', 'primary', 'references', 'right', 'rollback', 'select', 'set',
  'table', 'then', 'true', 'truncate', 'union', 'unique', 'update', 'values', 'view', 'when',
  'where', 'with',
)

const MYSQL_KEYWORDS = kw(
  ...SQL_KEYWORDS,
  'auto_increment', 'auto_commit', 'charset', 'collate', 'database', 'delayed', 'describe',
  'dual', 'engine', 'explain', 'fulltext', 'high_priority', 'ignore', 'isolation', 'kill',
  'level', 'lock', 'low_priority', 'match', 'optimize', 'partition', 'privileges', 'regexp',
  'release', 'repair', 'replace', 'restrict', 'revoke', 'rlike', 'savepoint', 'show', 'spatial',
  'sql_calc_found_rows', 'straight_join', 'unlock', 'unsigned', 'signed', 'transaction', 'tables',
  'use', 'varying', 'character', 'national',
)

const ORACLE_KEYWORDS = kw(
  ...SQL_KEYWORDS,
  'audit', 'body', 'connect', 'cursor', 'declare', 'exception', 'fetch', 'first', 'keep', 'last',
  'level', 'loop', 'matched', 'merge', 'minus', 'nvl', 'nvl2', 'open', 'package', 'pivot',
  'pragma', 'prior', 'procedure', 'raise', 'release', 'rowid', 'rownum', 'sequence', 'start',
  'savepoint', 'sysdate', 'systimestamp', 'tablespace', 'trigger', 'unpivot', 'validated',
  'validate', 'localtimestamp', 'cursor', 'record', 'gather', 'statistics', 'trace', 'plan',
  'explain', 'grant', 'revoke', 'connect', 'dictionary', 'flashback', 'purge', 'synonym',
  'materialized', 'profile', 'role', 'resource',
)

const SQLSERVER_KEYWORDS = kw(
  ...SQL_KEYWORDS,
  'apply', 'catch', 'delayed', 'fetch', 'go', 'getdate', 'getutcdate', 'holdlock', 'identity',
  'nolock', 'next', 'nowait', 'only', 'output', 'paglock', 'pivot', 'print', 'raiserror',
  'readonly', 'rowlock', 'rows', 'savetran', 'schema', 'scope_identity', 'tablock', 'then',
  'throw', 'tran', 'transaction', 'try', 'try_cast', 'try_convert', 'unpivot', 'updlock',
  'waitfor', 'while', 'xlock', 'rows', 'range', 'unbounded', 'preceding', 'following',
)

const POSTGRESQL_KEYWORDS = kw(
  ...SQL_KEYWORDS,
  'array', 'asymmetric', 'cascade', 'conflict', 'concurrently', 'cube', 'distinct', 'do',
  'filter', 'following', 'format', 'generated', 'grouping', 'groups', 'ilike', 'lateral',
  'language', 'nothing', 'nulls', 'oids', 'only', 'over', 'partition', 'plpgsql', 'preceding',
  'range', 'restrict', 'rollup', 'rows', 'similar', 'snapshot', 'tablesample', 'unlogged',
  'vacuum', 'verbose', 'window', 'collation', 'identity', 'jsonb', 'serial', 'bigserial',
)

const SQLITE_KEYWORDS = kw(
  ...SQL_KEYWORDS,
  'abort', 'attach', 'autoincrement', 'binary', 'cascade', 'collate', 'deferred', 'detach',
  'exclusive', 'explain', 'fail', 'glob', 'immediate', 'indexed', 'nocase', 'pragma', 'query',
  'reindex', 'replace', 'restrict', 'strict', 'vacuum', 'virtual', 'without', 'recursive',
  'conflict', 'stored', 'generated', 'wal', 'retry',
)

const MARIADB_KEYWORDS = kw(
  ...MYSQL_KEYWORDS,
  'again', 'boolean', 'check', 'column', 'extension', 'floating', 'fulltext', 'ignore', 'infile',
  'low_priority', 'mode', 'natural', 'optimize', 'outfile', 'partition', 'persistent', 'query',
  'rewrite', 'sequence', 'soname', 'spatial', 'ssl', 'storage', 'stored', 'swap', 'virtual',
  'with_parser', 'against', 'expansion',
)

const DB2_KEYWORDS = kw(
  ...SQL_KEYWORDS,
  'alias', 'bind', 'bucket', 'catalog', 'data', 'fetch', 'first', 'materialized', 'nickname',
  'next', 'only', 'package', 'precompile', 'rebind', 'rewrite', 'rows', 'sequence', 'trigger',
  'view', 'lateral', 'merge', 'nested', 'summary', 'table', 'validproc', 'volume', 'noupdate',
  'optimize', 'journal', 'activating', 'activation', 'range', 'for', 'evaluated', 'untyped',
  'current', 'date', 'time', 'timestamp',
)

const HIVE_KEYWORDS = kw(
  ...SQL_KEYWORDS,
  'add', 'archive', 'array', 'buckets', 'cluster', 'collect_set', 'distribute', 'explode',
  'extended', 'file', 'formatted', 'functions', 'inpath', 'jar', 'lateral', 'load', 'local',
  'macro', 'map', 'msck', 'orc', 'overwrite', 'parquet', 'partitions', 'pos_explode', 'reduce',
  'repair', 'restrict', 'row', 'serde', 'serdeproperties', 'sort', 'stack', 'stored', 'ties',
  'transform', 'using', 'window',
)

const CLICKHOUSE_KEYWORDS = kw(
  ...SQL_KEYWORDS,
  'array', 'asof', 'buffer', 'cluster', 'dictionary', 'distributed', 'final', 'format', 'global',
  'interpolate', 'json_each_row', 'lifetime', 'live', 'materialized', 'merge_tree', 'null',
  'prewhere', 'sample', 'settings', 'source', 'step', 'summing', 'tabseparated', 'tiny', 'view',
  'with_fill', 'any', 'exact', 'aggregate', 'replacing', 'collapsing', 'graphite', 'versioned',
)

const SHELL_KEYWORDS = kw(
  'alias', 'break', 'case', 'cd', 'continue', 'declare', 'do', 'done', 'echo', 'elif', 'else',
  'esac', 'eval', 'exec', 'exit', 'export', 'fi', 'for', 'function', 'if', 'in', 'let', 'local',
  'printf', 'read', 'readonly', 'return', 'select', 'set', 'shift', 'source', 'test', 'then',
  'time', 'typeset', 'unset', 'until', 'while',
)

const POWERSHELL_KEYWORDS = kw(
  'begin', 'break', 'catch', 'class', 'continue', 'default', 'do', 'else', 'elseif', 'end',
  'enum', 'exit', 'filter', 'finally', 'for', 'foreach', 'from', 'function', 'hidden', 'if', 'in',
  'interface', 'param', 'process', 'return', 'static', 'switch', 'throw', 'trap', 'try', 'until',
  'using', 'var', 'while', 'workflow', 'where', 'select',
)

const SWIFT_KEYWORDS = kw(
  'as', 'associatedtype', 'break', 'case', 'catch', 'class', 'continue', 'convenience', 'default',
  'defer', 'deinit', 'do', 'else', 'enum', 'extension', 'fallthrough', 'false', 'fileprivate',
  'final', 'for', 'func', 'get', 'guard', 'if', 'import', 'in', 'indirect', 'init', 'inout',
  'internal', 'is', 'lazy', 'let', 'mutating', 'nil', 'open', 'operator', 'override', 'private',
  'protocol', 'public', 'repeat', 'required', 'rethrows', 'return', 'self', 'set', 'some',
  'static', 'struct', 'subscript', 'super', 'switch', 'throw', 'throws', 'true', 'try', 'typealias',
  'unowned', 'var', 'weak', 'where', 'while',
)

const KOTLIN_KEYWORDS = kw(
  'as', 'break', 'class', 'continue', 'do', 'else', 'false', 'for', 'fun', 'if', 'in', 'interface',
  'is', 'null', 'object', 'package', 'return', 'super', 'this', 'throw', 'true', 'try', 'typealias',
  'typeof', 'val', 'var', 'when', 'while', 'by', 'catch', 'constructor', 'delegate', 'dynamic',
  'field', 'file', 'finally', 'get', 'import', 'init', 'param', 'property', 'receiver', 'set',
  'setparam', 'where', 'actual', 'abstract', 'annotation', 'companion', 'const', 'crossinline',
  'data', 'enum', 'expect', 'external', 'final', 'infix', 'inline', 'inner', 'internal', 'lateinit',
  'noinline', 'open', 'operator', 'out', 'override', 'private', 'protected', 'public', 'reified',
  'sealed', 'suspend', 'tailrec', 'vararg',
)

const R_KEYWORDS = kw(
  'break', 'else', 'FALSE', 'for', 'function', 'if', 'in', 'Inf', 'NA', 'NaN', 'next', 'NULL',
  'repeat', 'return', 'TRUE', 'while',
)

const LUA_KEYWORDS = kw(
  'and', 'break', 'do', 'else', 'elseif', 'end', 'false', 'for', 'function', 'goto', 'if', 'in',
  'local', 'nil', 'not', 'or', 'repeat', 'return', 'then', 'true', 'until', 'while',
)

const PERL_KEYWORDS = kw(
  'do', 'else', 'elsif', 'for', 'foreach', 'given', 'if', 'last', 'local', 'my', 'next', 'our',
  'package', 'redo', 'return', 'state', 'sub', 'unless', 'until', 'use', 'when', 'while',
)

const SCALA_KEYWORDS = kw(
  'abstract', 'case', 'catch', 'class', 'def', 'do', 'else', 'extends', 'false', 'final',
  'finally', 'for', 'forSome', 'if', 'implicit', 'import', 'lazy', 'match', 'new', 'null',
  'object', 'override', 'package', 'private', 'protected', 'return', 'sealed', 'super', 'this',
  'throw', 'trait', 'try', 'true', 'type', 'val', 'var', 'while', 'with', 'yield',
)

const HASKELL_KEYWORDS = kw(
  'as', 'case', 'class', 'data', 'default', 'deriving', 'do', 'else', 'foreign', 'if', 'import',
  'in', 'infix', 'infixl', 'infixr', 'instance', 'let', 'module', 'newtype', 'of', 'then',
  'type', 'where',
)

const GROOVY_KEYWORDS = kw(
  'as', 'assert', 'break', 'case', 'catch', 'class', 'const', 'continue', 'def', 'default', 'do',
  'else', 'enum', 'extends', 'false', 'finally', 'for', 'goto', 'if', 'implements', 'import', 'in',
  'instanceof', 'interface', 'new', 'null', 'package', 'return', 'super', 'switch', 'this',
  'throw', 'throws', 'trait', 'true', 'try', 'while',
)

const DOCKERFILE_KEYWORDS = kw(
  'ADD', 'ARG', 'CMD', 'COPY', 'ENTRYPOINT', 'ENV', 'EXPOSE', 'FROM', 'HEALTHCHECK', 'LABEL',
  'MAINTAINER', 'ONBUILD', 'RUN', 'SHELL', 'STOPSIGNAL', 'USER', 'VOLUME', 'WORKDIR',
)

const GRAPHQL_KEYWORDS = kw(
  'query', 'mutation', 'subscription', 'fragment', 'on', 'schema', 'type', 'interface', 'union',
  'enum', 'input', 'scalar', 'directive', 'extend', 'implements', 'true', 'false', 'null',
)

export const CODE_LANGUAGES: CodeLanguage[] = [
  { id: 'javascript', label: 'JavaScript', aliases: ['js', 'node', 'nodejs', 'mjs', 'cjs', 'ecmascript'], kind: 'source', lineComment: ['//'], blockComment: ['/*', '*/'], keywords: JS_KEYWORDS },
  { id: 'typescript', label: 'TypeScript', aliases: ['ts', 'tsx'], kind: 'source', lineComment: ['//'], blockComment: ['/*', '*/'], keywords: TS_KEYWORDS },
  { id: 'python', label: 'Python', aliases: ['py'], kind: 'source', lineComment: ['#'], blockComment: null, keywords: PYTHON_KEYWORDS },
  { id: 'java', label: 'Java', aliases: [], kind: 'source', lineComment: ['//'], blockComment: ['/*', '*/'], keywords: JAVA_KEYWORDS },
  { id: 'c', label: 'C', aliases: [], kind: 'source', lineComment: ['//'], blockComment: ['/*', '*/'], keywords: C_KEYWORDS },
  { id: 'cpp', label: 'C++', aliases: ['c++', 'cpp'], kind: 'source', lineComment: ['//'], blockComment: ['/*', '*/'], keywords: CPP_KEYWORDS },
  { id: 'csharp', label: 'C#', aliases: ['c#', 'cs', 'dotnet'], kind: 'source', lineComment: ['//'], blockComment: ['/*', '*/'], keywords: CSHARP_KEYWORDS },
  { id: 'go', label: 'Go', aliases: ['golang'], kind: 'source', lineComment: ['//'], blockComment: ['/*', '*/'], keywords: GO_KEYWORDS },
  { id: 'rust', label: 'Rust', aliases: ['rs'], kind: 'source', lineComment: ['//'], blockComment: ['/*', '*/'], keywords: RUST_KEYWORDS },
  { id: 'php', label: 'PHP', aliases: [], kind: 'source', lineComment: ['//', '#'], blockComment: ['/*', '*/'], keywords: PHP_KEYWORDS },
  { id: 'ruby', label: 'Ruby', aliases: ['rb'], kind: 'source', lineComment: ['#'], blockComment: null, keywords: RUBY_KEYWORDS },
  { id: 'swift', label: 'Swift', aliases: [], kind: 'source', lineComment: ['//'], blockComment: ['/*', '*/'], keywords: SWIFT_KEYWORDS },
  { id: 'kotlin', label: 'Kotlin', aliases: ['kt'], kind: 'source', lineComment: ['//'], blockComment: ['/*', '*/'], keywords: KOTLIN_KEYWORDS },
  { id: 'dart', label: 'Dart', aliases: ['flutter'], kind: 'source', lineComment: ['//'], blockComment: ['/*', '*/'], keywords: DART_KEYWORDS },
  { id: 'sql', label: 'SQL', aliases: ['ansi', 'standard', 'standard sql'], kind: 'sql', lineComment: ['--'], blockComment: ['/*', '*/'], keywords: SQL_KEYWORDS },
  { id: 'mysql', label: 'MySQL', aliases: ['mysql'], kind: 'sql', lineComment: ['--', '#'], blockComment: ['/*', '*/'], keywords: MYSQL_KEYWORDS },
  { id: 'mariadb', label: 'MariaDB', aliases: ['mariadb'], kind: 'sql', lineComment: ['--', '#'], blockComment: ['/*', '*/'], keywords: MARIADB_KEYWORDS },
  { id: 'oracle', label: 'Oracle', aliases: ['oracle', 'plsql', 'pl/sql'], kind: 'sql', lineComment: ['--'], blockComment: ['/*', '*/'], keywords: ORACLE_KEYWORDS },
  { id: 'sqlserver', label: 'SQL Server', aliases: ['mssql', 'sqlserver', 'tsql', 't-sql', 'sql server'], kind: 'sql', lineComment: ['--'], blockComment: ['/*', '*/'], keywords: SQLSERVER_KEYWORDS },
  { id: 'postgresql', label: 'PostgreSQL', aliases: ['postgres', 'postgresql', 'pg'], kind: 'sql', lineComment: ['--'], blockComment: ['/*', '*/'], keywords: POSTGRESQL_KEYWORDS },
  { id: 'sqlite', label: 'SQLite', aliases: ['sqlite'], kind: 'sql', lineComment: ['--'], blockComment: ['/*', '*/'], keywords: SQLITE_KEYWORDS },
  { id: 'db2', label: 'Db2', aliases: ['db2', 'ibm db2'], kind: 'sql', lineComment: ['--'], blockComment: ['/*', '*/'], keywords: DB2_KEYWORDS },
  { id: 'hive', label: 'Hive', aliases: ['hive', 'hiveql', 'hql'], kind: 'sql', lineComment: ['--'], blockComment: ['/*', '*/'], keywords: HIVE_KEYWORDS },
  { id: 'clickhouse', label: 'ClickHouse', aliases: ['clickhouse', 'ch'], kind: 'sql', lineComment: ['--'], blockComment: ['/*', '*/'], keywords: CLICKHOUSE_KEYWORDS },
  { id: 'shell', label: 'Shell / Bash', aliases: ['bash', 'sh', 'zsh', 'shell', 'shellscript'], kind: 'source', lineComment: ['#'], blockComment: null, keywords: SHELL_KEYWORDS },
  { id: 'powershell', label: 'PowerShell', aliases: ['ps1', 'pwsh'], kind: 'source', lineComment: ['#'], blockComment: null, keywords: POWERSHELL_KEYWORDS },
  { id: 'html', label: 'HTML / XML', aliases: ['htm', 'xhtml', 'xml', 'svg'], kind: 'markup', lineComment: [], blockComment: ['<!--', '-->'], keywords: [] },
  { id: 'css', label: 'CSS', aliases: [], kind: 'css', lineComment: [], blockComment: ['/*', '*/'], keywords: [] },
  { id: 'scss', label: 'SCSS / Sass', aliases: ['sass', 'less'], kind: 'css', lineComment: ['//'], blockComment: ['/*', '*/'], keywords: [] },
  { id: 'json', label: 'JSON', aliases: ['jsonc', 'json5'], kind: 'json', lineComment: [], blockComment: null, keywords: [] },
  { id: 'yaml', label: 'YAML', aliases: ['yml'], kind: 'json', lineComment: ['#'], blockComment: null, keywords: [] },
  { id: 'markdown', label: 'Markdown', aliases: ['md'], kind: 'markup', lineComment: [], blockComment: ['<!--', '-->'], keywords: [] },
  { id: 'r', label: 'R', aliases: ['rlang'], kind: 'source', lineComment: ['#'], blockComment: null, keywords: R_KEYWORDS },
  { id: 'lua', label: 'Lua', aliases: [], kind: 'source', lineComment: ['--'], blockComment: ['--[[', ']]'], keywords: LUA_KEYWORDS },
  { id: 'perl', label: 'Perl', aliases: ['pl'], kind: 'source', lineComment: ['#'], blockComment: null, keywords: PERL_KEYWORDS },
  { id: 'scala', label: 'Scala', aliases: [], kind: 'source', lineComment: ['//'], blockComment: ['/*', '*/'], keywords: SCALA_KEYWORDS },
  { id: 'haskell', label: 'Haskell', aliases: ['hs'], kind: 'source', lineComment: ['--'], blockComment: ['{-', '-}'], keywords: HASKELL_KEYWORDS },
  { id: 'groovy', label: 'Groovy', aliases: ['gradle'], kind: 'source', lineComment: ['//'], blockComment: ['/*', '*/'], keywords: GROOVY_KEYWORDS },
  { id: 'objectivec', label: 'Objective-C', aliases: ['objc', 'obj-c'], kind: 'source', lineComment: ['//'], blockComment: ['/*', '*/'], keywords: C_KEYWORDS },
  { id: 'dockerfile', label: 'Dockerfile', aliases: ['docker'], kind: 'source', lineComment: ['#'], blockComment: null, keywords: DOCKERFILE_KEYWORDS },
  { id: 'graphql', label: 'GraphQL', aliases: ['gql'], kind: 'source', lineComment: ['#'], blockComment: null, keywords: GRAPHQL_KEYWORDS },
  { id: 'text', label: '纯文本', aliases: ['text', 'plaintext', 'plain', 'txt'], kind: 'text', lineComment: [], blockComment: null, keywords: [] },
]

const TEXT_LANGUAGE: CodeLanguage = { id: 'text', label: '纯文本', aliases: [], kind: 'text', lineComment: [], blockComment: null, keywords: [] }

const LANGUAGE_MAP = new Map<string, CodeLanguage>()
for (const language of CODE_LANGUAGES) {
  LANGUAGE_MAP.set(language.id, language)
  LANGUAGE_MAP.set(language.label.toLocaleLowerCase(), language)
  for (const alias of language.aliases) LANGUAGE_MAP.set(alias.toLocaleLowerCase(), language)
}

/** Resolve a stored label / alias to a canonical language definition (falls back to plain text). */
export function getLanguage(input: string | null | undefined): CodeLanguage {
  if (!input) return TEXT_LANGUAGE
  return LANGUAGE_MAP.get(input.trim().toLocaleLowerCase()) ?? TEXT_LANGUAGE
}

/** Normalize a stored label / alias to the canonical display label. */
export function normalizeLanguageLabel(input: string | null | undefined): string {
  if (!input) return TEXT_LANGUAGE.label
  return getLanguage(input).label
}

// Stable per-language hues for colored language badges. The fallback hashes the
// label so every unknown language still gets a consistent, distinct color.
const CURATED_HUES: Record<string, number> = {
  javascript: 52, typescript: 220, python: 205, java: 18, c: 204, cpp: 204, csharp: 262,
  go: 145, rust: 18, php: 265, ruby: 0, swift: 28, kotlin: 262, dart: 202, sql: 34,
  mysql: 218, mariadb: 245, oracle: 0, sqlserver: 220, postgresql: 210, sqlite: 205,
  db2: 210, hive: 45, clickhouse: 14, shell: 90, powershell: 214, html: 16, css: 225,
  scss: 275, json: 42, yaml: 32, markdown: 195, r: 200, lua: 218, perl: 265, scala: 0,
  haskell: 250, groovy: 345, objectivec: 210, dockerfile: 212, graphql: 300, text: 210,
}

export function languageHue(input: string | null | undefined): number {
  const language = getLanguage(input)
  const curated = CURATED_HUES[language.id]
  if (curated !== undefined) return curated
  let hash = 0
  const label = input ?? ''
  for (let i = 0; i < label.length; i += 1) hash = (hash * 31 + label.charCodeAt(i)) >>> 0
  return hash % 360
}

export function languageBadgeStyle(input: string | null | undefined): Record<string, string> {
  const hue = languageHue(input)
  return {
    color: `hsla(${hue}, 72%, 62%, 1)`,
    backgroundColor: `hsla(${hue}, 72%, 62%, 0.16)`,
    borderColor: `hsla(${hue}, 72%, 62%, 0.35)`,
  }
}
