import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
  type ButtonHTMLAttributes,
} from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import {
  AlertCircle,
  Check,
  ChevronLeft,
  ChevronRight,
  LoaderCircle,
  X,
  Inbox,
} from 'lucide-react';
import clsx from 'clsx';
import { initials, statusLabels, priorityLabels } from '../lib/format';
import type { Person, Priority, TaskStatus, Page } from '../lib/types';
import { ApiError } from '../lib/api';

export function Button({
  variant = 'secondary',
  busy,
  children,
  className,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  busy?: boolean;
}) {
  return (
    <button
      {...props}
      className={clsx('button', `button-${variant}`, className)}
      disabled={props.disabled || busy}
    >
      {busy && <LoaderCircle size={15} className="spin" />}
      {children}
    </button>
  );
}
export function Avatar({
  person,
  size = 'small',
}: {
  person: Pick<Person, 'fullName' | 'id'>;
  size?: 'small' | 'medium' | 'large';
}) {
  const tint = person.id.split('').reduce((v, c) => v + c.charCodeAt(0), 0) % 6;
  return (
    <span className={`avatar avatar-${size} avatar-${tint}`} title={person.fullName}>
      {initials(person.fullName)}
    </span>
  );
}
export function StatusBadge({ status }: { status: TaskStatus }) {
  return (
    <span className={`status status-${status.toLowerCase()}`}>
      <span className="status-dot" />
      {statusLabels[status]}
    </span>
  );
}
export function PriorityBadge({ priority }: { priority: Priority }) {
  return (
    <span className={`priority priority-${priority.toLowerCase()}`}>
      {priorityLabels[priority]}
    </span>
  );
}
export function PageHeading({
  title,
  description,
  actions,
}: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      {actions && <div className="heading-actions">{actions}</div>}
    </div>
  );
}
export function Empty({
  title = 'No tasks here',
  description,
  action,
}: {
  title?: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="empty-state">
      <Inbox size={28} strokeWidth={1.3} />
      <h3>{title}</h3>
      <p>{description}</p>
      {action}
    </div>
  );
}
export function Loading({ rows = 5 }: { rows?: number }) {
  return (
    <div className="loading-rows" role="status" aria-label="Loading">
      <span className="sr-only">Loading…</span>
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="skeleton-row">
          <div className="skeleton circle" />
          <div className="skeleton" />
          <div className="skeleton" />
        </div>
      ))}
    </div>
  );
}
export function ErrorState({ error, retry }: { error: unknown; retry?: () => void }) {
  return (
    <div className="error-state" role="alert">
      <AlertCircle size={18} />
      <span>{error instanceof Error ? error.message : 'Unable to load this information.'}</span>
      {retry && (
        <Button variant="ghost" onClick={retry}>
          Try again
        </Button>
      )}
    </div>
  );
}
export function FormError({ error }: { error: unknown }) {
  if (!error) return null;
  return (
    <div className="form-error" role="alert">
      {error instanceof ApiError && Object.keys(error.errors).length
        ? Object.entries(error.errors).map(([key, value]) => (
            <p key={key}>
              {key}: {value}
            </p>
          ))
        : error instanceof Error
          ? error.message
          : String(error)}
    </div>
  );
}
export function Modal({
  open,
  onClose,
  title,
  description,
  children,
  wide = false,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: ReactNode;
  wide?: boolean;
}) {
  return (
    <Dialog.Root
      open={open}
      onOpenChange={(value) => {
        if (!value) onClose();
      }}
    >
      <Dialog.Portal>
        <Dialog.Overlay className="overlay" />
        <Dialog.Content
          className={clsx('modal', wide && 'modal-wide')}
          aria-describedby={description ? 'modal-description' : undefined}
        >
          <div className="modal-header">
            <Dialog.Title>{title}</Dialog.Title>
            <Dialog.Close asChild>
              <button className="icon-button" aria-label="Close dialog">
                <X size={18} />
              </button>
            </Dialog.Close>
          </div>
          {description && (
            <Dialog.Description id="modal-description" className="modal-description">
              {description}
            </Dialog.Description>
          )}
          {children}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
export function Confirm({
  open,
  onClose,
  onConfirm,
  title,
  description,
  busy,
}: {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  description: string;
  busy?: boolean;
}) {
  return (
    <Modal open={open} onClose={onClose} title={title} description={description}>
      <div className="form-actions">
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="danger" busy={busy} onClick={onConfirm}>
          Confirm
        </Button>
      </div>
    </Modal>
  );
}
export function Pagination({
  data,
  onPage,
}: {
  data: Pick<Page<unknown>, 'page' | 'size' | 'totalElements' | 'totalPages'>;
  onPage: (page: number) => void;
}) {
  return (
    <div className="pagination">
      <span>
        {data.totalElements === 0
          ? '0 results'
          : `${data.page * data.size + 1}–${Math.min((data.page + 1) * data.size, data.totalElements)} of ${data.totalElements}`}
      </span>
      <div>
        <Button
          aria-label="Previous page"
          disabled={data.page === 0}
          onClick={() => onPage(data.page - 1)}
        >
          <ChevronLeft size={16} />
        </Button>
        <span>
          Page {data.page + 1} of {Math.max(1, data.totalPages)}
        </span>
        <Button
          aria-label="Next page"
          disabled={data.page + 1 >= data.totalPages}
          onClick={() => onPage(data.page + 1)}
        >
          <ChevronRight size={16} />
        </Button>
      </div>
    </div>
  );
}
const ToastContext = createContext<(message: string, kind?: 'success' | 'error') => void>(() => {});
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<{ message: string; kind: string } | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  useEffect(() => () => clearTimeout(timer.current), []);
  return (
    <ToastContext.Provider
      value={(message, kind = 'success') => {
        setToast({ message, kind });
        clearTimeout(timer.current);
        timer.current = setTimeout(() => setToast(null), 5000);
      }}
    >
      {children}
      {toast && (
        <div className={`toast toast-${toast.kind}`} role="status">
          {toast.kind === 'error' ? <AlertCircle size={17} /> : <Check size={17} />}
          <span>{toast.message}</span>
          <button aria-label="Dismiss notification" onClick={() => setToast(null)}>
            <X size={15} />
          </button>
        </div>
      )}
    </ToastContext.Provider>
  );
}
export const useToast = () => useContext(ToastContext);
