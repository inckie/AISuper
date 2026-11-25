import React, { Fragment, type CSSProperties } from 'react';
import {
  booleanOrNull,
  floatOrNull,
  parseColorOrNull,
  resolveDropdownOptions,
  resolveDynamicWidgets,
  resolveStyleRule,
  stringOrNull
} from './layoutUtils';
import type { JsonValue, StyleSheet, Widget, StyleRule } from './types';

interface RenderProps {
  widget: Widget;
  values: Record<string, JsonValue>;
  styleSheet?: StyleSheet | null;
  onValueChange: (id: string, value: string) => void;
  onAction: (action: string, args: JsonValue[]) => void;
  parentDirection?: 'column' | 'row';
}

function widgetBaseStyle(widget: Widget, style: StyleRule): CSSProperties {
  const textAlign = style.textAlign?.toLowerCase();
  const alignment = style.alignment?.toLowerCase();

  let alignSelf: CSSProperties['alignSelf'] = undefined;
  if (alignment === 'start' || alignment === 'left') {
    alignSelf = 'flex-start';
  } else if (alignment === 'end' || alignment === 'right') {
    alignSelf = 'flex-end';
  } else if (alignment === 'center') {
    alignSelf = 'center';
  }

  const hasNoBreak =
    style.noBreak === true ||
    style.noWrap === true ||
    style.whiteSpace === 'nowrap' ||
    style.whiteSpace === 'no-wrap' ||
    (widget as any).noBreak === true;

  const whiteSpace = hasNoBreak ? 'nowrap' : (style.whiteSpace as any) ?? undefined;
  const textOverflow = style.overflow === 'ellipsis' || hasNoBreak ? 'ellipsis' : undefined;

  return {
    width: widget.fillMaxWidth ? '100%' : undefined,
    height: widget.fillMaxSize ? '100%' : undefined,
    minHeight: widget.classes?.includes('screen') ? '100%' : undefined,
    flex: widget.weight != null ? `${widget.weight} 1 0` : undefined,
    alignSelf,
    color: parseColorOrNull(style.textColor),
    background: parseColorOrNull(style.backgroundColor),
    borderRadius: style.cornerRadius != null ? `${style.cornerRadius}px` : undefined,
    fontSize: style.fontSize != null ? `${style.fontSize}px` : undefined,
    fontWeight: style.fontWeight ?? undefined,
    whiteSpace,
    textOverflow,
    overflow: hasNoBreak ? 'hidden' : undefined,
    textAlign:
      textAlign === 'center' || textAlign === 'right' || textAlign === 'left' || textAlign === 'justify'
        ? textAlign
        : undefined,
    padding:
      style.padding != null
        ? `${style.padding}px`
        : style.paddingVertical != null || style.paddingHorizontal != null
          ? `${style.paddingVertical ?? 0}px ${style.paddingHorizontal ?? 0}px`
          : undefined,
    boxSizing: 'border-box'
  };
}

export function WidgetRenderer({
  widget,
  values,
  styleSheet,
  onValueChange,
  onAction,
  parentDirection = 'column'
}: RenderProps): JSX.Element | null {
  const visible = widget.visibilityId ? booleanOrNull(values[widget.visibilityId]) ?? true : true;
  if (!visible) return null;

  const style = resolveStyleRule(widget, styleSheet);
  const baseStyle = widgetBaseStyle(widget, style);
  const isLight = styleSheet?.scheme?.toLowerCase() === 'light';
  const accentColor = parseColorOrNull(styleSheet?.tokens?.accentColor) ?? (isLight ? '#2563EB' : '#00A4EF');

  switch (widget.type) {
    case 'Column': {
      const children = widget.children ?? [];
      const dynamic = widget.dynamicChildrenId ? resolveDynamicWidgets(values[widget.dynamicChildrenId]) : [];
      const allChildren = [...children, ...dynamic];
      return (
        <div
          className={widget.isScrollable ? 'metro-scrollbar' : undefined}
          style={{
            ...baseStyle,
            display: 'flex',
            flexDirection: 'column',
            gap: 10,
            overflowY: widget.isScrollable ? 'auto' : undefined,
            touchAction: widget.isScrollable ? 'pan-y' : undefined
          }}
        >
          {allChildren.map((child, index) => (
            <Fragment key={`${child.id ?? child.type}-${index}`}>
              <WidgetRenderer
                widget={child}
                values={values}
                styleSheet={styleSheet}
                onValueChange={onValueChange}
                onAction={onAction}
                parentDirection="column"
              />
            </Fragment>
          ))}
        </div>
      );
    }

    case 'Row': {
      const children = widget.children ?? [];
      const dynamic = widget.dynamicChildrenId ? resolveDynamicWidgets(values[widget.dynamicChildrenId]) : [];
      const allChildren = [...children, ...dynamic];
      return (
        <div
          className={widget.isScrollable ? 'metro-scrollbar' : undefined}
          style={{
            ...baseStyle,
            display: 'flex',
            flexDirection: 'row',
            alignItems: 'center',
            gap: 10,
            overflowX: widget.isScrollable ? 'auto' : undefined,
            overflowY: widget.isScrollable ? 'hidden' : undefined,
            touchAction: widget.isScrollable ? 'pan-x' : undefined
          }}
        >
          {allChildren.map((child, index) => (
            <Fragment key={`${child.id ?? child.type}-${index}`}>
              <WidgetRenderer
                widget={child}
                values={values}
                styleSheet={styleSheet}
                onValueChange={onValueChange}
                onAction={onAction}
                parentDirection="row"
              />
            </Fragment>
          ))}
        </div>
      );
    }

    case 'Text': {
      const widgetId = widget.id ?? undefined;
      const displayText = widgetId ? stringOrNull(values[widgetId]) ?? widget.text ?? '' : widget.text ?? '';
      return (
        <div
          style={{
            ...baseStyle,
            color: baseStyle.color ?? (isLight ? '#1F2937' : '#f8fafc'),
            lineHeight: 1.4
          }}
        >
          {displayText}
        </div>
      );
    }

    case 'TextField': {
      const widgetId = widget.id ?? undefined;
      const value = widgetId ? stringOrNull(values[widgetId]) ?? '' : '';
      return (
        <input
          type={widget.password ? 'password' : 'text'}
          style={{
            ...baseStyle,
            width: widget.fillMaxWidth ? '100%' : baseStyle.width ?? '100%',
            minHeight: 40,
            padding: '8px 14px',
            borderRadius: baseStyle.borderRadius ?? 6,
            border: isLight ? '1px solid rgba(15, 23, 42, 0.16)' : '1px solid rgba(255, 255, 255, 0.15)',
            background: baseStyle.background ?? (isLight ? '#F3F4F6' : 'rgba(255, 255, 255, 0.05)'),
            color: baseStyle.color ?? (isLight ? '#111827' : '#ffffff'),
            fontSize: 14,
            outline: 'none',
            backdropFilter: 'blur(10px)'
          }}
          value={value}
          placeholder={widget.hint ?? ''}
          onChange={(e) => {
            if (widgetId) {
              onValueChange(widgetId, e.target.value);
            }
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              const actionName = widget.onImeAction ?? widget.imeAction;
              if (actionName) {
                onAction(actionName, []);
              }
            }
          }}
        />
      );
    }

    case 'Button': {
      const isGhost = baseStyle.background === 'transparent';
      return (
        <button
          style={{
            ...baseStyle,
            minHeight: 38,
            padding: baseStyle.padding ?? '8px 16px',
            borderRadius: baseStyle.borderRadius ?? 6,
            border: isGhost
              ? '1px solid transparent'
              : isLight
                ? '1px solid rgba(15, 23, 42, 0.12)'
                : '1px solid rgba(0, 164, 239, 0.4)',
            background: baseStyle.background ?? 'rgba(0, 164, 239, 0.15)',
            color: baseStyle.color ?? accentColor,
            fontWeight: 600,
            fontSize: 13,
            cursor: 'pointer',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            transition: 'all 0.15s ease',
            whiteSpace: 'nowrap'
          }}
          onClick={() => {
            if (widget.action) {
              onAction(widget.action, widget.actionArgs ?? []);
            }
          }}
        >
          {widget.icon && <img src={widget.icon} alt="" style={{ width: 16, height: 16 }} />}
          <span>{widget.text ?? 'Button'}</span>
        </button>
      );
    }

    case 'Image': {
      const widgetId = widget.id ?? undefined;
      const dataFromValues = widgetId ? stringOrNull(values[widgetId]) : null;
      const src = dataFromValues || widget.data?.trim() || widget.url || '';
      if (!src) return null;
      return (
        <img
          src={src}
          alt={widget.description ?? 'image'}
          style={{
            ...baseStyle,
            objectFit: 'contain'
          }}
        />
      );
    }

    case 'Dropdown': {
      const widgetId = widget.id ?? undefined;
      const currentVal = widgetId ? stringOrNull(values[widgetId]) ?? '' : '';
      const options = resolveDropdownOptions(widget, values);
      return (
        <select
          style={{
            ...baseStyle,
            minHeight: 38,
            padding: '8px 12px',
            borderRadius: baseStyle.borderRadius ?? 6,
            border: isLight ? '1px solid rgba(15, 23, 42, 0.16)' : '1px solid rgba(255, 255, 255, 0.15)',
            background: baseStyle.background ?? (isLight ? '#ffffff' : '#1a1a1a'),
            color: baseStyle.color ?? (isLight ? '#111827' : '#ffffff'),
            outline: 'none'
          }}
          value={currentVal}
          onChange={(e) => {
            if (widgetId) {
              onValueChange(widgetId, e.target.value);
            }
            if (widget.onChangeAction) {
              onAction(widget.onChangeAction, [e.target.value]);
            }
          }}
        >
          {widget.hint && <option value="" disabled>{widget.hint}</option>}
          {options.map((opt, i) => (
            <option key={i} value={opt.value}>
              {opt.label ?? opt.value}
            </option>
          ))}
        </select>
      );
    }

    case 'Switch': {
      const widgetId = widget.id ?? undefined;
      const isChecked = widgetId ? booleanOrNull(values[widgetId]) ?? widget.checked ?? false : widget.checked ?? false;
      return (
        <label
          style={{
            ...baseStyle,
            display: 'inline-flex',
            alignItems: 'center',
            gap: 10,
            cursor: 'pointer'
          }}
        >
          <input
            type="checkbox"
            checked={isChecked}
            style={{ width: 18, height: 18, accentColor }}
            onChange={(e) => {
              const checked = e.target.checked;
              if (widgetId) {
                onValueChange(widgetId, String(checked));
              }
              if (widget.onChangeAction) {
                onAction(widget.onChangeAction, widget.actionArgs ?? [checked]);
              }
            }}
          />
          {widget.text && (
            <span style={{ color: baseStyle.color ?? (isLight ? '#1F2937' : '#e2e8f0'), fontSize: 14 }}>
              {widget.text}
            </span>
          )}
        </label>
      );
    }

    case 'Spinner': {
      return (
        <div
          style={{
            ...baseStyle,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 16
          }}
        >
          <div
            style={{
              width: 24,
              height: 24,
              borderRadius: '50%',
              border: isLight ? '2px solid rgba(15, 23, 42, 0.12)' : '2px solid rgba(0, 164, 239, 0.2)',
              borderTopColor: accentColor,
              animation: 'spin 0.8s linear infinite'
            }}
          />
        </div>
      );
    }

    case 'Progress': {
      const progressVal = widget.progressId ? floatOrNull(values[widget.progressId]) ?? widget.progress ?? 0 : widget.progress ?? 0;
      const isIndeterminate = widget.indeterminate === true;
      return (
        <div
          style={{
            ...baseStyle,
            width: widget.fillMaxWidth ? '100%' : '100%',
            height: 4,
            borderRadius: 2,
            background: isLight ? 'rgba(15, 23, 42, 0.12)' : 'rgba(255, 255, 255, 0.1)',
            overflow: 'hidden',
            position: 'relative'
          }}
        >
          <div
            style={{
              width: isIndeterminate ? '40%' : `${Math.min(100, Math.max(0, progressVal * 100))}%`,
              height: '100%',
              background: accentColor,
              animation: isIndeterminate ? 'pulse 1.2s ease-in-out infinite' : undefined,
              transition: 'width 0.2s ease'
            }}
          />
        </div>
      );
    }

    case 'Table': {
      const rows = (widget.rows && widget.rows.length > 0 ? widget.rows : widget.children) ?? [];
      const dynamicId = widget.dynamicRowsId ?? widget.dynamicChildrenId;
      const dynamicRows = dynamicId ? resolveDynamicWidgets(values[dynamicId]) : [];
      const allRows = [...rows, ...dynamicRows];
      const cols = widget.columns ?? [];

      const isVertical = widget.scrollDirection === 'vertical';
      const isHorizontal = widget.isScrollable && !isVertical;

      return (
        <div
          className={widget.isScrollable ? 'metro-scrollbar' : undefined}
          style={{
            ...baseStyle,
            width: widget.fillMaxWidth ? '100%' : baseStyle.width,
            display: 'flex',
            flexDirection: 'column',
            overflowX: isHorizontal ? 'auto' : undefined,
            overflowY: isVertical ? 'auto' : widget.isScrollable ? 'hidden' : undefined,
            touchAction: isHorizontal ? 'pan-x' : isVertical ? 'pan-y' : undefined,
            flexShrink: 0,
            gap: 2
          }}
        >
          {allRows.map((row, rIdx) => {
            const rowChildren = (row as any).children ?? [row];
            const rowStyle = resolveStyleRule(row, styleSheet);
            const rowBase = widgetBaseStyle(row, rowStyle);
            return (
              <div
                key={`${row.id ?? 'row'}-${rIdx}`}
                style={{
                  ...rowBase,
                  display: 'flex',
                  flexDirection: 'row',
                  alignItems: 'center',
                  width: widget.fillMaxWidth ? '100%' : 'max-content'
                }}
              >
                {rowChildren.map((cell: Widget, cIdx: number) => {
                  const colDef = cols[cIdx];
                  const cellStyle: CSSProperties = { boxSizing: 'border-box' };
                  if (colDef) {
                    if (colDef.width != null) {
                      cellStyle.width = `${colDef.width}px`;
                      cellStyle.minWidth = `${colDef.width}px`;
                      cellStyle.flexShrink = 0;
                    } else if (colDef.weight != null) {
                      cellStyle.flex = `${colDef.weight} 1 0%`;
                    }
                  }
                  return (
                    <div key={`${cell.id ?? 'cell'}-${cIdx}`} style={cellStyle}>
                      <WidgetRenderer
                        widget={cell}
                        values={values}
                        styleSheet={styleSheet}
                        onValueChange={onValueChange}
                        onAction={onAction}
                        parentDirection="row"
                      />
                    </div>
                  );
                })}
              </div>
            );
          })}
        </div>
      );
    }

    case 'TableRow': {
      const children = widget.children ?? [];
      return (
        <div
          style={{
            ...baseStyle,
            display: 'flex',
            flexDirection: 'row',
            alignItems: 'center',
            width: '100%'
          }}
        >
          {children.map((child, index) => (
            <Fragment key={`${child.id ?? child.type}-${index}`}>
              <WidgetRenderer
                widget={child}
                values={values}
                styleSheet={styleSheet}
                onValueChange={onValueChange}
                onAction={onAction}
                parentDirection="row"
              />
            </Fragment>
          ))}
        </div>
      );
    }

    default:
      return null;
  }
}
